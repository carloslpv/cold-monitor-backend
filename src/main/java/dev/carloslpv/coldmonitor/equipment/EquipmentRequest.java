package dev.carloslpv.coldmonitor.equipment;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Payload to register or update an equipment. {@code active} is optional and defaults to {@code true}.
 */
public record EquipmentRequest(
		@NotBlank @Size(max = 50) String code,
		@NotBlank @Size(max = 120) String name,
		@Size(max = 120) String location,
		@NotNull EquipmentType type,
		@NotNull @DecimalMin("-100.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal minTemperature,
		@NotNull @DecimalMin("-100.00") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal maxTemperature,
		Boolean active) {

	@AssertTrue(message = "minTemperature must be lower than maxTemperature")
	public boolean isThresholdRangeValid() {
		return minTemperature == null || maxTemperature == null || minTemperature.compareTo(maxTemperature) < 0;
	}

	boolean activeOrDefault() {
		return active == null || active;
	}

}
