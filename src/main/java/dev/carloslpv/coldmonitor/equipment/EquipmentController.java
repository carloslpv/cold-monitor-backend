package dev.carloslpv.coldmonitor.equipment;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/equipment")
public class EquipmentController {

	private final EquipmentService service;

	public EquipmentController(EquipmentService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<EquipmentResponse> register(@Valid @RequestBody EquipmentRequest request) {
		var equipment = service.register(request);
		var location = ServletUriComponentsBuilder.fromCurrentRequest()
			.path("/{id}")
			.buildAndExpand(equipment.getId())
			.toUri();
		return ResponseEntity.created(location).body(EquipmentResponse.from(equipment));
	}

	@GetMapping
	public List<EquipmentResponse> list(@RequestParam(required = false) Boolean active) {
		return service.list(active).stream().map(EquipmentResponse::from).toList();
	}

	@GetMapping("/{id}")
	public EquipmentResponse get(@PathVariable UUID id) {
		return EquipmentResponse.from(service.get(id));
	}

	@PutMapping("/{id}")
	public EquipmentResponse update(@PathVariable UUID id, @Valid @RequestBody EquipmentRequest request) {
		return EquipmentResponse.from(service.update(id, request));
	}

}
