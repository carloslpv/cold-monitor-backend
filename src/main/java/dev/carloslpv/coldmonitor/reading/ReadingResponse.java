package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ReadingResponse(
		UUID id,
		UUID equipmentId,
		String equipmentCode,
		BigDecimal temperature,
		Instant recordedAt,
		Instant receivedAt,
		ReadingStatus status,
		BigDecimal minTemperature,
		BigDecimal maxTemperature) {

	static ReadingResponse from(TemperatureReading reading) {
		var equipment = reading.getEquipment();
		return new ReadingResponse(reading.getId(), equipment.getId(), equipment.getCode(), reading.getTemperature(),
				reading.getRecordedAt(), reading.getReceivedAt(), reading.getStatus(), reading.getMinTemperature(),
				reading.getMaxTemperature());
	}

}
