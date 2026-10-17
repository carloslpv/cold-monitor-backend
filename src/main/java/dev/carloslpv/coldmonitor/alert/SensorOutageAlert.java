package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.reading.ReadingResponse;
import dev.carloslpv.coldmonitor.reading.ReadingStatus;

/**
 * Published inside a transaction when an equipment stops sending readings or starts again. Alert channels and the live
 * stream pick it up after commit.
 *
 * @param endedAt arrival of the first reading after the silence; {@code null} for {@link Kind#STARTED}
 * @param reading the reading that ended the outage; {@code null} for {@link Kind#STARTED}
 * @param excursion the excursion that was open when the sensor went silent, if any; {@code null} for
 * {@link Kind#ENDED}
 */
public record SensorOutageAlert(
		Kind kind,
		UUID outageId,
		UUID equipmentId,
		String equipmentCode,
		String equipmentName,
		String equipmentLocation,
		Instant lastReadingAt,
		BigDecimal lastTemperature,
		Instant detectedAt,
		Instant endedAt,
		ReadingResponse reading,
		OpenExcursion excursion) {

	public enum Kind {

		STARTED,

		ENDED

	}

	/** Where the temperature stood when the sensor went silent mid-excursion. */
	public record OpenExcursion(ReadingStatus status, Instant startedAt, BigDecimal peakTemperature) {

	}

	static SensorOutageAlert started(SensorOutage outage, Excursion openExcursion) {
		var excursion = (openExcursion != null) ? new OpenExcursion(openExcursion.getStatus(),
				openExcursion.getStartedAt(), openExcursion.getPeakTemperature()) : null;
		return of(Kind.STARTED, outage, null, excursion);
	}

	static SensorOutageAlert ended(SensorOutage outage, ReadingResponse reading) {
		return of(Kind.ENDED, outage, reading, null);
	}

	private static SensorOutageAlert of(Kind kind, SensorOutage outage, ReadingResponse reading,
			OpenExcursion excursion) {
		var equipment = outage.getEquipment();
		return new SensorOutageAlert(kind, outage.getId(), equipment.getId(), equipment.getCode(), equipment.getName(),
				equipment.getLocation(), outage.getLastReadingAt(), outage.getLastTemperature(),
				outage.getDetectedAt(), outage.getEndedAt(), reading, excursion);
	}

}
