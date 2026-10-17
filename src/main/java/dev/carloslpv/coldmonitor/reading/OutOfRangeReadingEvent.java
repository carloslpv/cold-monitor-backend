package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when a stored reading falls outside its equipment thresholds. Alerting hooks in by listening to it.
 */
public record OutOfRangeReadingEvent(
		UUID readingId,
		UUID equipmentId,
		String equipmentCode,
		String equipmentName,
		BigDecimal temperature,
		BigDecimal minTemperature,
		BigDecimal maxTemperature,
		ReadingStatus status,
		Instant recordedAt) {

	static OutOfRangeReadingEvent from(TemperatureReading reading) {
		var equipment = reading.getEquipment();
		return new OutOfRangeReadingEvent(reading.getId(), equipment.getId(), equipment.getCode(),
				equipment.getName(), reading.getTemperature(), reading.getMinTemperature(),
				reading.getMaxTemperature(), reading.getStatus(), reading.getRecordedAt());
	}

}
