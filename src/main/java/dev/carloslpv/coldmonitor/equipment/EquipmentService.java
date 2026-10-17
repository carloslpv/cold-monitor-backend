package dev.carloslpv.coldmonitor.equipment;

import java.util.List;
import java.util.UUID;

import dev.carloslpv.coldmonitor.common.ConflictException;
import dev.carloslpv.coldmonitor.common.NotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EquipmentService {

	private final EquipmentRepository repository;

	public EquipmentService(EquipmentRepository repository) {
		this.repository = repository;
	}

	@Transactional
	public Equipment register(EquipmentRequest request) {
		if (repository.existsByCode(request.code())) {
			throw duplicateCode(request.code());
		}
		return repository.save(new Equipment(request.code(), request.name(), request.location(), request.type(),
				request.minTemperature(), request.maxTemperature(), request.activeOrDefault()));
	}

	@Transactional
	public Equipment update(UUID id, EquipmentRequest request) {
		var equipment = get(id);
		if (repository.existsByCodeAndIdNot(request.code(), id)) {
			throw duplicateCode(request.code());
		}
		equipment.update(request.code(), request.name(), request.location(), request.type(),
				request.minTemperature(), request.maxTemperature(), request.activeOrDefault());
		return repository.saveAndFlush(equipment);
	}

	@Transactional(readOnly = true)
	public Equipment get(UUID id) {
		return repository.findById(id)
			.orElseThrow(() -> new NotFoundException("Equipment %s not found".formatted(id)));
	}

	@Transactional(readOnly = true)
	public List<Equipment> list(Boolean active) {
		return (active != null) ? repository.findByActiveOrderByNameAsc(active) : repository.findAllByOrderByNameAsc();
	}

	private static ConflictException duplicateCode(String code) {
		return new ConflictException("Equipment code '%s' is already in use".formatted(code));
	}

}
