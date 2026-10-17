package dev.carloslpv.coldmonitor.reading;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.common.BadRequestException;
import dev.carloslpv.coldmonitor.common.ConflictException;
import dev.carloslpv.coldmonitor.common.NotFoundException;
import dev.carloslpv.coldmonitor.equipment.EquipmentService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReadingService {

	/** How far ahead of the server clock a sensor timestamp may be before it is rejected. */
	static final Duration MAX_CLOCK_SKEW = Duration.ofMinutes(5);

	private final TemperatureReadingRepository repository;

	private final EquipmentService equipmentService;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	public ReadingService(TemperatureReadingRepository repository, EquipmentService equipmentService,
			ApplicationEventPublisher events, Clock clock) {
		this.repository = repository;
		this.equipmentService = equipmentService;
		this.events = events;
		this.clock = clock;
	}

	@Transactional
	public TemperatureReading record(UUID equipmentId, ReadingRequest request) {
		var equipment = equipmentService.get(equipmentId);
		if (!equipment.isActive()) {
			throw new ConflictException("Equipment %s is inactive and does not accept readings".formatted(equipmentId));
		}
		var now = clock.instant();
		var recordedAt = (request.recordedAt() != null) ? request.recordedAt() : now;
		if (recordedAt.isAfter(now.plus(MAX_CLOCK_SKEW))) {
			throw new BadRequestException("recordedAt %s is in the future".formatted(recordedAt));
		}
		var reading = repository.save(new TemperatureReading(equipment, request.temperature(), recordedAt, now));
		events.publishEvent(ReadingRecordedEvent.from(reading));
		if (reading.getStatus().isOutOfRange()) {
			events.publishEvent(OutOfRangeReadingEvent.from(reading));
		}
		return reading;
	}

	@Transactional(readOnly = true)
	public Page<TemperatureReading> history(UUID equipmentId, TimeWindow window, Pageable pageable) {
		equipmentService.get(equipmentId);
		return repository.findByEquipmentIdAndRecordedAtBetweenOrderByRecordedAtDesc(equipmentId, window.from(),
				window.to(), pageable);
	}

	@Transactional(readOnly = true)
	public TemperatureReading latest(UUID equipmentId) {
		equipmentService.get(equipmentId);
		return repository.findFirstByEquipmentIdOrderByRecordedAtDesc(equipmentId)
			.orElseThrow(() -> new NotFoundException("Equipment %s has no readings yet".formatted(equipmentId)));
	}

	@Transactional(readOnly = true)
	public Page<TemperatureReading> outOfRange(UUID equipmentId, TimeWindow window, Pageable pageable) {
		if (equipmentId == null) {
			return repository.findByStatusNotAndRecordedAtBetweenOrderByRecordedAtDesc(ReadingStatus.IN_RANGE,
					window.from(), window.to(), pageable);
		}
		return repository.findByEquipmentIdAndStatusNotAndRecordedAtBetweenOrderByRecordedAtDesc(equipmentId,
				ReadingStatus.IN_RANGE, window.from(), window.to(), pageable);
	}

	/**
	 * Resolves an optional {@code [from, to]} query window, defaulting to the last 24 hours.
	 */
	public TimeWindow window(Instant from, Instant to) {
		var end = (to != null) ? to : clock.instant();
		var start = (from != null) ? from : end.minus(Duration.ofHours(24));
		if (start.isAfter(end)) {
			throw new BadRequestException("'from' must not be after 'to'");
		}
		return new TimeWindow(start, end);
	}

	public record TimeWindow(Instant from, Instant to) {
	}

}
