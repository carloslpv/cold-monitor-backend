package dev.carloslpv.coldmonitor.alert;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AlertProperties.class)
class AlertConfiguration {

	/**
	 * How many excursions and sensor outages are open right now. Read from the database on every scrape, so the values
	 * survive restarts; both are counts over small partial indexes.
	 */
	@Bean
	MeterBinder openIncidentMetrics(ExcursionRepository excursions, SensorOutageRepository outages) {
		return (registry) -> {
			Gauge.builder("coldmonitor.excursions.open", excursions, ExcursionRepository::countByEndedAtIsNull)
				.description("Equipment currently out of its temperature range")
				.register(registry);
			Gauge.builder("coldmonitor.sensor.outages.open", outages, SensorOutageRepository::countByEndedAtIsNull)
				.description("Equipment whose sensor stopped sending readings")
				.register(registry);
		};
	}

}
