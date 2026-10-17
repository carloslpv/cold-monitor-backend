package dev.carloslpv.coldmonitor.equipment;

import dev.carloslpv.coldmonitor.reading.ReadingStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells live stream clients, such as the dashboard, when an equipment is registered or changed, so they pick up new
 * thresholds and equipment that was activated or deactivated without a page reload.
 */
@Component
class EquipmentBroadcaster {

	static final String EVENT_NAME = "equipment";

	private final ReadingStream stream;

	EquipmentBroadcaster(ReadingStream stream) {
		this.stream = stream;
	}

	@TransactionalEventListener
	void onEquipmentChanged(EquipmentChangedEvent event) {
		this.stream.publish(EVENT_NAME, event.equipment().id().toString(), event.equipment());
	}

}
