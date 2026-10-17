package dev.carloslpv.coldmonitor.alert;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SensorOutageRepository extends JpaRepository<SensorOutage, UUID> {

	Optional<SensorOutage> findByEquipmentIdAndEndedAtIsNull(UUID equipmentId);

	@EntityGraph(attributePaths = "equipment")
	List<SensorOutage> findByEndedAtIsNullOrderByDetectedAtDesc();

	@EntityGraph(attributePaths = "equipment")
	List<SensorOutage> findByDetectedAtBetweenOrderByDetectedAtDesc(Instant from, Instant to);

	@EntityGraph(attributePaths = "equipment")
	List<SensorOutage> findByEquipmentIdOrderByDetectedAtAsc(UUID equipmentId);

	long countByEndedAtIsNull();

	@Query("select o.equipment.id from SensorOutage o where o.endedAt is null")
	List<UUID> findOpenEquipmentIds();

	/** When each active equipment's latest reading arrived; {@code null} for equipment that never sent one. */
	@Query("""
			select new dev.carloslpv.coldmonitor.alert.LastArrival(e.id,
				(select max(r.receivedAt) from TemperatureReading r where r.equipment = e))
			from Equipment e where e.active = true""")
	List<LastArrival> findLastArrivals();

}
