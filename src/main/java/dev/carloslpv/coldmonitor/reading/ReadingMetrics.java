package dev.carloslpv.coldmonitor.reading;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes the reading flow as metrics once each reading is committed: a counter per equipment and status, and the
 * latest temperature and limits of every equipment, so a dashboard can draw each equipment against its range.
 * <p>
 * Equipment is tagged by code, which stays a handful of series per equipment. A series appears with the first reading
 * after startup.
 */
@Component
class ReadingMetrics {

	private final MeterRegistry registry;

	private final Map<String, Latest> latest = new ConcurrentHashMap<>();

	ReadingMetrics(MeterRegistry registry, ReadingStream stream) {
		this.registry = registry;
		Gauge.builder("coldmonitor.stream.clients", stream, ReadingStream::clientCount)
			.description("Clients connected to the live reading stream")
			.register(registry);
	}

	@TransactionalEventListener
	void onReadingRecorded(ReadingRecordedEvent event) {
		var reading = event.reading();
		Counter.builder("coldmonitor.readings")
			.description("Readings stored")
			.tag("equipment", reading.equipmentCode())
			.tag("status", reading.status().name())
			.register(this.registry)
			.increment();
		this.latest.computeIfAbsent(reading.equipmentCode(), this::register).update(reading);
	}

	private Latest register(String equipmentCode) {
		var latest = new Latest();
		Gauge.builder("coldmonitor.temperature", latest, (l) -> l.temperature)
			.description("Latest temperature reported by the equipment")
			.baseUnit("celsius")
			.tag("equipment", equipmentCode)
			.register(this.registry);
		Gauge.builder("coldmonitor.temperature.limit", latest, (l) -> l.min)
			.description("Allowed temperature range of the equipment")
			.baseUnit("celsius")
			.tags("equipment", equipmentCode, "bound", "min")
			.register(this.registry);
		Gauge.builder("coldmonitor.temperature.limit", latest, (l) -> l.max)
			.description("Allowed temperature range of the equipment")
			.baseUnit("celsius")
			.tags("equipment", equipmentCode, "bound", "max")
			.register(this.registry);
		return latest;
	}

	private static final class Latest {

		private Instant recordedAt = Instant.MIN;

		private volatile double temperature = Double.NaN;

		private volatile double min = Double.NaN;

		private volatile double max = Double.NaN;

		/** Keeps the newest reading, so a late one (e.g. a sensor flushing its buffer) does not replace it. */
		synchronized void update(ReadingResponse reading) {
			if (reading.recordedAt().isBefore(this.recordedAt)) {
				return;
			}
			this.recordedAt = reading.recordedAt();
			this.temperature = reading.temperature().doubleValue();
			this.min = reading.minTemperature().doubleValue();
			this.max = reading.maxTemperature().doubleValue();
		}

	}

}
