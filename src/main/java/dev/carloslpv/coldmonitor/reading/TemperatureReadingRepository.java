package dev.carloslpv.coldmonitor.reading;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemperatureReadingRepository extends JpaRepository<TemperatureReading, UUID> {

	@EntityGraph(attributePaths = "equipment")
	Page<TemperatureReading> findByEquipmentIdAndRecordedAtBetweenOrderByRecordedAtDesc(UUID equipmentId,
			Instant from, Instant to, Pageable pageable);

	@EntityGraph(attributePaths = "equipment")
	Optional<TemperatureReading> findFirstByEquipmentIdOrderByRecordedAtDesc(UUID equipmentId);

	Optional<TemperatureReading> findFirstByEquipmentIdOrderByReceivedAtDesc(UUID equipmentId);

	boolean existsByEquipmentIdAndRecordedAtAfter(UUID equipmentId, Instant recordedAt);

	@EntityGraph(attributePaths = "equipment")
	Page<TemperatureReading> findByStatusNotAndRecordedAtBetweenOrderByRecordedAtDesc(ReadingStatus status,
			Instant from, Instant to, Pageable pageable);

	@EntityGraph(attributePaths = "equipment")
	Page<TemperatureReading> findByEquipmentIdAndStatusNotAndRecordedAtBetweenOrderByRecordedAtDesc(UUID equipmentId,
			ReadingStatus status, Instant from, Instant to, Pageable pageable);

}
