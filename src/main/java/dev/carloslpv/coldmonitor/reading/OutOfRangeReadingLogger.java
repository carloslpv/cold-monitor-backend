package dev.carloslpv.coldmonitor.reading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Logs every out-of-range reading once it is committed. Alerts are sent per excursion, not per reading, by the
 * {@code alert} package.
 */
@Component
class OutOfRangeReadingLogger {

	private static final Logger logger = LoggerFactory.getLogger(OutOfRangeReadingLogger.class);

	@TransactionalEventListener
	void onOutOfRange(OutOfRangeReadingEvent event) {
		logger.info("Out-of-range reading on {} ({}): {} °C is {} (allowed {} to {} °C) at {}", event.equipmentCode(),
				event.equipmentName(), event.temperature(), event.status(), event.minTemperature(),
				event.maxTemperature(), event.recordedAt());
	}

}
