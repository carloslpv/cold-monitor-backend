package dev.carloslpv.coldmonitor.alert;

import dev.carloslpv.coldmonitor.reading.ReadingStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells live stream clients, such as the dashboard, when an equipment goes offline or comes back.
 */
@Component
class SensorOutageBroadcaster {

	static final String EVENT_NAME = "sensor-outage";

	private final ReadingStream stream;

	SensorOutageBroadcaster(ReadingStream stream) {
		this.stream = stream;
	}

	@TransactionalEventListener
	void onSensorOutage(SensorOutageAlert alert) {
		this.stream.publish(EVENT_NAME, alert.outageId().toString(), SensorOutageResponse.from(alert));
	}

}
