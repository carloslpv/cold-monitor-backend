package dev.carloslpv.coldmonitor.common;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets browser clients such as the dashboard call the API from another origin, and enables the scheduled stream
 * heartbeat.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(WebConfiguration.CorsProperties.class)
class WebConfiguration implements WebMvcConfigurer {

	private final CorsProperties cors;

	WebConfiguration(CorsProperties cors) {
		this.cors = cors;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
			.allowedOrigins(cors.allowedOrigins().toArray(String[]::new))
			.allowedMethods("GET", "POST", "PUT")
			.allowedHeaders("*")
			.exposedHeaders("Location")
			// the dashboard dev server sends its session cookie cross-origin
			.allowCredentials(true);
	}

	@ConfigurationProperties("cold-monitor.cors")
	record CorsProperties(List<String> allowedOrigins) {

		CorsProperties {
			allowedOrigins = (allowedOrigins != null) ? List.copyOf(allowedOrigins) : List.of();
		}

	}

}
