package dev.carloslpv.coldmonitor.equipment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EquipmentRepository extends JpaRepository<Equipment, UUID> {

	List<Equipment> findAllByOrderByNameAsc();

	List<Equipment> findByActiveOrderByNameAsc(boolean active);

	boolean existsByCode(String code);

	boolean existsByCodeAndIdNot(String code, UUID id);

}
