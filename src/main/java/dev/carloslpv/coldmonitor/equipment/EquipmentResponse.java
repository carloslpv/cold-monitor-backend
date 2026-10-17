package dev.carloslpv.coldmonitor.equipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EquipmentResponse(
		UUID id,
		String code,
		String name,
		String location,
		EquipmentType type,
		BigDecimal minTemperature,
		BigDecimal maxTemperature,
		boolean active,
		Instant createdAt,
		Instant updatedAt) {

	static EquipmentResponse from(Equipment equipment) {
		return new EquipmentResponse(equipment.getId(), equipment.getCode(), equipment.getName(),
				equipment.getLocation(), equipment.getType(), equipment.getMinTemperature(),
				equipment.getMaxTemperature(), equipment.isActive(), equipment.getCreatedAt(),
				equipment.getUpdatedAt());
	}

}
