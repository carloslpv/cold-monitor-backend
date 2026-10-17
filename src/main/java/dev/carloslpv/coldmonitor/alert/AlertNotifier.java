package dev.carloslpv.coldmonitor.alert;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Sends excursion and sensor outage alerts once the change that caused them is committed. Every alert is logged; it
 * also goes to Telegram when a bot token and chat id are configured. Deliveries run one at a time on a background
 * thread, so ingestion never waits on Telegram and an alert's end never arrives before its start.
 */
@Component
class AlertNotifier {

	static final int MAX_ATTEMPTS = 3;

	static final Duration RETRY_DELAY = Duration.ofSeconds(2);

	private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(10);

	private static final Logger logger = LoggerFactory.getLogger(AlertNotifier.class);

	private final AlertMessages messages;

	private final TelegramClient telegram;

	private final ExecutorService sender = Executors
		.newSingleThreadExecutor(Thread.ofVirtual().name("telegram-sender").factory());

	AlertNotifier(AlertProperties properties) {
		this.messages = new AlertMessages(properties.timeZone());
		if (properties.telegram().isConfigured()) {
			this.telegram = new TelegramClient(properties.telegram());
			logger.info("Telegram alerts on, sending to chat {}", properties.telegram().chatId());
		}
		else {
			this.telegram = null;
			logger.info("Telegram alerts off: set TELEGRAM_BOT_TOKEN and TELEGRAM_CHAT_ID to turn them on");
		}
	}

	@TransactionalEventListener
	void onExcursion(ExcursionAlert alert) {
		send("Excursion " + alert.kind(), alert.equipmentCode(), this.messages.format(alert));
	}

	@TransactionalEventListener
	void onSensorOutage(SensorOutageAlert alert) {
		send("Sensor outage " + alert.kind(), alert.equipmentCode(), this.messages.format(alert));
	}

	private void send(String alert, String equipmentCode, String text) {
		logger.warn("{} on {}: {}", alert, equipmentCode, text.replace("\n", " | "));
		if (this.telegram != null) {
			this.sender.execute(() -> deliver(alert, equipmentCode, text));
		}
	}

	private void deliver(String alert, String equipmentCode, String text) {
		for (var attempt = 1;; attempt++) {
			Duration delay;
			try {
				this.telegram.sendMessage(text);
				return;
			}
			catch (HttpClientErrorException.TooManyRequests ex) {
				delay = TelegramClient.retryAfter(ex).orElse(RETRY_DELAY.multipliedBy(attempt));
			}
			catch (HttpClientErrorException ex) {
				// a wrong token or chat id: retrying cannot help
				logger.error("Telegram rejected the {} alert for {}: {} {}", alert, equipmentCode, ex.getStatusCode(),
						ex.getResponseBodyAsString());
				return;
			}
			catch (RestClientException ex) {
				delay = RETRY_DELAY.multipliedBy(attempt);
				if (attempt < MAX_ATTEMPTS) {
					logger.warn("Telegram delivery failed (attempt {}/{}), retrying in {} s: {}", attempt,
							MAX_ATTEMPTS, delay.toSeconds(), this.telegram.redact(ex.getMessage()));
				}
				else {
					logger.error("Could not deliver the {} alert for {} to Telegram after {} attempts: {}", alert,
							equipmentCode, MAX_ATTEMPTS, this.telegram.redact(ex.getMessage()));
				}
			}
			if (attempt >= MAX_ATTEMPTS) {
				return;
			}
			try {
				Thread.sleep(delay);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
	}

	/** Gives alerts already queued a chance to go out before the application stops. */
	@EventListener(ContextClosedEvent.class)
	void shutdown() throws InterruptedException {
		this.sender.shutdown();
		if (!this.sender.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
			this.sender.shutdownNow();
		}
	}

}
