package dev.carloslpv.coldmonitor.equipment;

/**
 * Published when an equipment is registered or updated, including its thresholds and {@code active} flag.
 */
public record EquipmentChangedEvent(EquipmentResponse equipment) {
}
