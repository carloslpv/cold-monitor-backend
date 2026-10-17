package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * A reading sent by a sensor. {@code recordedAt} is the sensor timestamp; when omitted the server time is used.
 */
public record ReadingRequest(
		@NotNull @DecimalMin("-100.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal temperature,
		Instant recordedAt) {
}
