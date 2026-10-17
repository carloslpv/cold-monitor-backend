package dev.carloslpv.coldmonitor.alert;

import java.time.Instant;
import java.util.UUID;

/**
 * When the latest reading of an equipment reached the server, or {@code null} if it never sent one.
 */
public record LastArrival(UUID equipmentId, Instant receivedAt) {

}
