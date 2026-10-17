package dev.carloslpv.coldmonitor.alert;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import dev.carloslpv.coldmonitor.equipment.Equipment;
import dev.carloslpv.coldmonitor.reading.ReadingRecordedEvent;
import dev.carloslpv.coldmonitor.reading.TemperatureReadingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Notices equipment that stopped sending readings. A periodic check opens a {@link SensorOutage} once an active
 * equipment has been silent for {@code offline-after}, and the next reading of that equipment closes it, publishing a
 * {@link SensorOutageAlert} for each transition.
 * <p>
 * Silence is measured from the later of the last arrival and the moment the check started running, so restarting the
 * backend (when no reading can arrive) does not flag every sensor at once.
 */
@Component
class SensorWatchdog {

	private static final Logger logger = LoggerFactory.getLogger(SensorWatchdog.class);

	private final SensorOutageRepository outages;

	private final ExcursionRepository excursions;

	private final TemperatureReadingRepository readings;

	private final EntityManager entityManager;

	private final ApplicationEventPublisher events;

	private final TransactionTemplate transactions;

	private final TaskScheduler scheduler;

	private final Clock clock;

	private final AlertProperties properties;

	private volatile Instant watchingSince;

	private ScheduledFuture<?> task;

	SensorWatchdog(SensorOutageRepository outages, ExcursionRepository excursions,
			TemperatureReadingRepository readings, EntityManager entityManager, ApplicationEventPublisher events,
			TransactionTemplate transactions, TaskScheduler scheduler, Clock clock, AlertProperties properties) {
		this.outages = outages;
		this.excursions = excursions;
		this.readings = readings;
		this.entityManager = entityManager;
		this.events = events;
		this.transactions = transactions;
		this.scheduler = scheduler;
		this.clock = clock;
		this.properties = properties;
	}

	@EventListener(ApplicationReadyEvent.class)
	void start() {
		this.watchingSince = this.clock.instant();
		this.task = this.scheduler.scheduleWithFixedDelay(this::check, this.properties.offlineCheckInterval());
		logger.info("Flagging equipment as offline after {} without readings (checked every {})",
				this.properties.offlineAfter(), this.properties.offlineCheckInterval());
	}

	@EventListener(ContextClosedEvent.class)
	void stop() {
		if (this.task != null) {
			this.task.cancel(false);
		}
	}

	/** Opens an outage for every active equipment that has been silent for too long. */
	void check() {
		var now = this.clock.instant();
		var silentSince = now.minus(this.properties.offlineAfter());
		if (this.watchingSince == null || this.watchingSince.isAfter(silentSince)) {
			return;
		}
		var open = new HashSet<>(this.outages.findOpenEquipmentIds());
		for (var arrival : this.outages.findLastArrivals()) {
			// equipment that never sent a reading has no sensor to lose yet
			if (arrival.receivedAt() == null || arrival.receivedAt().isAfter(silentSince)
					|| open.contains(arrival.equipmentId())) {
				continue;
			}
			try {
				this.transactions.executeWithoutResult((status) -> openOutage(arrival.equipmentId(), silentSince, now));
			}
			catch (RuntimeException ex) {
				logger.error("Could not open a sensor outage for equipment {}", arrival.equipmentId(), ex);
			}
		}
	}

	private void openOutage(UUID equipmentId, Instant silentSince, Instant now) {
		// the same lock readings take, so a reading arriving right now either lands first or closes this outage
		var equipment = this.entityManager.find(Equipment.class, equipmentId, LockModeType.PESSIMISTIC_WRITE);
		if (equipment == null || !equipment.isActive()
				|| this.outages.findByEquipmentIdAndEndedAtIsNull(equipmentId).isPresent()) {
			return;
		}
		var last = this.readings.findFirstByEquipmentIdOrderByReceivedAtDesc(equipmentId).orElse(null);
		if (last == null || last.getReceivedAt().isAfter(silentSince)) {
			return;
		}
		var outage = this.outages
			.save(new SensorOutage(equipment, last.getReceivedAt(), last.getTemperature(), now));
		var excursion = this.excursions.findByEquipmentIdAndEndedAtIsNull(equipmentId).orElse(null);
		this.events.publishEvent(SensorOutageAlert.started(outage, excursion));
	}

	/**
	 * Any reading of an equipment with an open outage closes it. Runs before the excursion tracker, so "readings are
	 * back" goes out before an alert about the reading itself.
	 */
	@EventListener
	@Order(Ordered.HIGHEST_PRECEDENCE)
	void onReadingRecorded(ReadingRecordedEvent event) {
		var reading = event.reading();
		this.entityManager.find(Equipment.class, reading.equipmentId(), LockModeType.PESSIMISTIC_WRITE);
		this.outages.findByEquipmentIdAndEndedAtIsNull(reading.equipmentId()).ifPresent((outage) -> {
			outage.end(reading.receivedAt(), reading.temperature());
			this.events.publishEvent(SensorOutageAlert.ended(outage, reading));
		});
	}

}
