package dev.carloslpv.coldmonitor.reading;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Pushes every committed reading to the connected Server-Sent Events clients. Sends happen on a single background
 * thread so a slow client never delays ingestion and events reach each client in commit order.
 */
@Component
public class ReadingStream {

	/** Clients reconnect on their own after this, so the server never holds a connection forever. */
	static final Duration TIMEOUT = Duration.ofMinutes(30);

	static final Duration RECONNECT_DELAY = Duration.ofSeconds(3);

	private static final Logger logger = LoggerFactory.getLogger(ReadingStream.class);

	private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

	private final ExecutorService sender = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("sse-sender").factory());

	public SseEmitter subscribe() {
		var emitter = new SseEmitter(TIMEOUT.toMillis());
		emitter.onCompletion(() -> emitters.remove(emitter));
		emitter.onTimeout(() -> emitters.remove(emitter));
		emitter.onError((ex) -> emitters.remove(emitter));
		try {
			emitter.send(SseEmitter.event().reconnectTime(RECONNECT_DELAY.toMillis()).comment("connected"));
		}
		catch (IOException ex) {
			emitter.completeWithError(ex);
			return emitter;
		}
		emitters.add(emitter);
		return emitter;
	}

	@TransactionalEventListener
	void onReadingRecorded(ReadingRecordedEvent event) {
		var reading = event.reading();
		broadcast(() -> SseEmitter.event()
			.name("reading")
			.id(reading.id().toString())
			.data(reading, MediaType.APPLICATION_JSON));
	}

	/** Keeps idle connections open through proxies and lets the server notice clients that went away. */
	@Scheduled(fixedRate = 15_000, initialDelay = 15_000)
	void heartbeat() {
		broadcast(() -> SseEmitter.event().comment("keep-alive"));
	}

	int subscriberCount() {
		return emitters.size();
	}

	// SseEventBuilder is single use, so each client gets a fresh one
	private void broadcast(Supplier<SseEventBuilder> event) {
		if (emitters.isEmpty()) {
			return;
		}
		sender.execute(() -> {
			for (var emitter : emitters) {
				try {
					emitter.send(event.get());
				}
				catch (IOException | IllegalStateException ex) {
					// the container reports the broken connection to the emitter itself
					logger.debug("Dropping stream client: {}", ex.getMessage());
					emitters.remove(emitter);
				}
			}
		});
	}

	/**
	 * Ends open streams as soon as shutdown starts. Graceful shutdown waits for in-flight requests, and a stream would
	 * otherwise hold it until the shutdown timeout.
	 */
	@EventListener(ContextClosedEvent.class)
	void shutdown() {
		sender.shutdownNow();
		emitters.forEach(SseEmitter::complete);
		emitters.clear();
	}

}
