package dev.carloslpv.coldmonitor.alert;

import dev.carloslpv.coldmonitor.equipment.Equipment;
import dev.carloslpv.coldmonitor.reading.ReadingRecordedEvent;
import dev.carloslpv.coldmonitor.reading.TemperatureReadingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns the reading flow into excursions: opens one on the first out-of-range reading of an equipment and closes it
 * once the temperature is back in range, publishing an {@link ExcursionAlert} for each transition. Runs inside the
 * reading transaction, so the excursion state commits or rolls back together with the reading.
 */
@Component
class ExcursionTracker {

	private final ExcursionRepository excursions;

	private final TemperatureReadingRepository readings;

	private final EntityManager entityManager;

	private final ApplicationEventPublisher events;

	private final AlertProperties properties;

	ExcursionTracker(ExcursionRepository excursions, TemperatureReadingRepository readings,
			EntityManager entityManager, ApplicationEventPublisher events, AlertProperties properties) {
		this.excursions = excursions;
		this.readings = readings;
		this.entityManager = entityManager;
		this.events = events;
		this.properties = properties;
	}

	@EventListener
	void onReadingRecorded(ReadingRecordedEvent event) {
		var reading = event.reading();
		// serializes readings of the same equipment so two of them cannot both open an excursion
		var equipment = this.entityManager.find(Equipment.class, reading.equipmentId(),
				LockModeType.PESSIMISTIC_WRITE);
		// a late reading (e.g. a sensor flushing its buffer) is stored but says nothing about the current state
		if (this.readings.existsByEquipmentIdAndRecordedAtAfter(reading.equipmentId(), reading.recordedAt())) {
			return;
		}
		var open = this.excursions.findByEquipmentIdAndEndedAtIsNull(reading.equipmentId());
		if (reading.status().isOutOfRange()) {
			if (open.isPresent()) {
				open.get().observeOutOfRange(reading.temperature());
				return;
			}
			var excursion = this.excursions.save(new Excursion(equipment, reading.status(), reading.minTemperature(),
					reading.maxTemperature(), reading.temperature(), reading.recordedAt()));
			this.events.publishEvent(ExcursionAlert.started(excursion));
		}
		else if (open.isPresent() && open.get()
			.observeInRange(reading.temperature(), reading.recordedAt(), this.properties.recoveryReadings())) {
			this.events.publishEvent(ExcursionAlert.ended(open.get()));
		}
	}

}
