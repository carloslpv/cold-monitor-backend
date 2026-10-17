package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;

/**
 * Where a reading falls relative to its equipment thresholds. Both limits are inclusive: a reading equal to the
 * minimum or maximum is still in range.
 */
public enum ReadingStatus {

	IN_RANGE,

	BELOW_MIN,

	ABOVE_MAX;

	public static ReadingStatus classify(BigDecimal temperature, BigDecimal min, BigDecimal max) {
		if (temperature.compareTo(min) < 0) {
			return BELOW_MIN;
		}
		if (temperature.compareTo(max) > 0) {
			return ABOVE_MAX;
		}
		return IN_RANGE;
	}

	public boolean isOutOfRange() {
		return this != IN_RANGE;
	}

}
