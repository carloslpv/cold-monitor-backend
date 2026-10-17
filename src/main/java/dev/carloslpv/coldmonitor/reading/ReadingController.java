package dev.carloslpv.coldmonitor.reading;

import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.common.PageResponse;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReadingController {

	private final ReadingService service;

	public ReadingController(ReadingService service) {
		this.service = service;
	}

	@PostMapping("/equipment/{equipmentId}/readings")
	@ResponseStatus(HttpStatus.CREATED)
	public ReadingResponse record(@PathVariable UUID equipmentId, @Valid @RequestBody ReadingRequest request) {
		return ReadingResponse.from(service.record(equipmentId, request));
	}

	@GetMapping("/equipment/{equipmentId}/readings")
	public PageResponse<ReadingResponse> history(@PathVariable UUID equipmentId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@PageableDefault(size = 100) Pageable pageable) {
		var page = service.history(equipmentId, service.window(from, to), pageable);
		return PageResponse.from(page.map(ReadingResponse::from));
	}

	@GetMapping("/equipment/{equipmentId}/readings/latest")
	public ReadingResponse latest(@PathVariable UUID equipmentId) {
		return ReadingResponse.from(service.latest(equipmentId));
	}

	@GetMapping("/readings/out-of-range")
	public PageResponse<ReadingResponse> outOfRange(@RequestParam(required = false) UUID equipmentId,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
			@PageableDefault(size = 100) Pageable pageable) {
		var page = service.outOfRange(equipmentId, service.window(from, to), pageable);
		return PageResponse.from(page.map(ReadingResponse::from));
	}

}
