package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.reading.ReadingStatus;

/**
 * Published inside the reading transaction when an excursion starts or ends. Alert channels send it after commit.
 *
 * @param temperature the reading that triggered the alert: the first out-of-range one, or the last in-range one that
 * closed the excursion
 * @param endedAt {@code null} for {@link Kind#STARTED}
 */
public record ExcursionAlert(
		Kind kind,
		UUID excursionId,
		String equipmentCode,
		String equipmentName,
		String equipmentLocation,
		ReadingStatus status,
		BigDecimal temperature,
		BigDecimal peakTemperature,
		BigDecimal minTemperature,
		BigDecimal maxTemperature,
		Instant startedAt,
		Instant endedAt) {

	public enum Kind {

		STARTED,

		ENDED

	}

	static ExcursionAlert started(Excursion excursion) {
		return of(Kind.STARTED, excursion, excursion.getStartTemperature());
	}

	static ExcursionAlert ended(Excursion excursion) {
		return of(Kind.ENDED, excursion, excursion.getEndTemperature());
	}

	private static ExcursionAlert of(Kind kind, Excursion excursion, BigDecimal temperature) {
		var equipment = excursion.getEquipment();
		return new ExcursionAlert(kind, excursion.getId(), equipment.getCode(), equipment.getName(),
				equipment.getLocation(), excursion.getStatus(), temperature, excursion.getPeakTemperature(),
				excursion.getMinTemperature(), excursion.getMaxTemperature(), excursion.getStartedAt(),
				excursion.getEndedAt());
	}

}
