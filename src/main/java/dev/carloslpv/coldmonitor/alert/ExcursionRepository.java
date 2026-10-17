package dev.carloslpv.coldmonitor.alert;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ExcursionRepository extends JpaRepository<Excursion, UUID> {

	Optional<Excursion> findByEquipmentIdAndEndedAtIsNull(UUID equipmentId);

	List<Excursion> findByEquipmentIdOrderByStartedAtAsc(UUID equipmentId);

	long countByEndedAtIsNull();

}
