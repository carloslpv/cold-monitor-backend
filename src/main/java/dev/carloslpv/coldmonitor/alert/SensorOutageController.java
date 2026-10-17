package dev.carloslpv.coldmonitor.alert;

import java.time.Instant;
import java.util.List;

import dev.carloslpv.coldmonitor.reading.ReadingService;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sensor-outages")
class SensorOutageController {

	private final SensorOutageRepository outages;

	private final ReadingService readings;

	SensorOutageController(SensorOutageRepository outages, ReadingService readings) {
		this.outages = outages;
		this.readings = readings;
	}

	/**
	 * With {@code open=true}, the outages still going on; otherwise those detected in {@code [from, to]} (the last 24
	 * hours by default). Newest first.
	 */
	@GetMapping
	@Transactional(readOnly = true)
	public List<SensorOutageResponse> list(@RequestParam(defaultValue = "false") boolean open,
			@RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to) {
		var list = open ? this.outages.findByEndedAtIsNullOrderByDetectedAtDesc() : window(from, to);
		return list.stream().map(SensorOutageResponse::from).toList();
	}

	private List<SensorOutage> window(Instant from, Instant to) {
		var window = this.readings.window(from, to);
		return this.outages.findByDetectedAtBetweenOrderByDetectedAtDesc(window.from(), window.to());
	}

}
