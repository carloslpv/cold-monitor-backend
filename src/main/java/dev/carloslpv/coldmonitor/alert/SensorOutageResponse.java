package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * @param endedAt arrival of the first reading after the silence; {@code null} while the outage is open
 */
public record SensorOutageResponse(
		UUID id,
		UUID equipmentId,
		String equipmentCode,
		Instant lastReadingAt,
		BigDecimal lastTemperature,
		Instant detectedAt,
		Instant endedAt,
		BigDecimal endTemperature) {

	static SensorOutageResponse from(SensorOutage outage) {
		var equipment = outage.getEquipment();
		return new SensorOutageResponse(outage.getId(), equipment.getId(), equipment.getCode(),
				outage.getLastReadingAt(), outage.getLastTemperature(), outage.getDetectedAt(), outage.getEndedAt(),
				outage.getEndTemperature());
	}

	static SensorOutageResponse from(SensorOutageAlert alert) {
		return new SensorOutageResponse(alert.outageId(), alert.equipmentId(), alert.equipmentCode(),
				alert.lastReadingAt(), alert.lastTemperature(), alert.detectedAt(), alert.endedAt(),
				(alert.reading() != null) ? alert.reading().temperature() : null);
	}

}
