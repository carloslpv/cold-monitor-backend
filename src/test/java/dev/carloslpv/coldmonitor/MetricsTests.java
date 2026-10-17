package dev.carloslpv.coldmonitor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The actuator on a port of its own, as in the Docker image: Prometheus scrapes it without logging in, while the main
 * port keeps it behind the administrator.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class MetricsTests {

	private final HttpClient http = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@LocalManagementPort
	private int managementPort;

	@Test
	void servesDomainMetricsOnTheManagementPortWithoutLogin() throws Exception {
		var equipment = postAsDevice("/api/v1/equipment", """
				{"code": "METRICS-01", "name": "Metrics fridge", "type": "REFRIGERATOR",
				 "minTemperature": 2.0, "maxTemperature": 8.0}
				""");
		assertThat(equipment.statusCode()).isEqualTo(201);
		String id = JsonPath.read(equipment.body(), "$.id");
		var reading = postAsDevice("/api/v1/equipment/" + id + "/readings", "{\"temperature\": 9.5}");
		assertThat(reading.statusCode()).isEqualTo(201);

		var metrics = get(managementPort, "/actuator/prometheus");

		assertThat(metrics.statusCode()).isEqualTo(200);
		assertThat(metrics.body())
			.contains("coldmonitor_readings_total{application=\"cold-monitor-backend\",equipment=\"METRICS-01\","
					+ "status=\"ABOVE_MAX\"} 1")
			.contains("coldmonitor_temperature_celsius{application=\"cold-monitor-backend\",equipment=\"METRICS-01\"} 9.5")
			.contains("coldmonitor_temperature_limit_celsius{application=\"cold-monitor-backend\",bound=\"max\","
					+ "equipment=\"METRICS-01\"} 8")
			.contains("coldmonitor_excursions_open{application=\"cold-monitor-backend\"} 1")
			.contains("coldmonitor_alerts_total{application=\"cold-monitor-backend\",kind=\"started\","
					+ "type=\"excursion\"} 1")
			.contains("coldmonitor_sensor_outages_open")
			.contains("http_server_requests_seconds_bucket");
	}

	@Test
	void keepsTheHealthCheckOnTheManagementPort() throws Exception {
		assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
	}

	@Test
	void keepsMetricsBehindTheAdminLoginOnTheMainPort() throws Exception {
		assertThat(get(port, "/actuator/prometheus").statusCode()).isEqualTo(401);
	}

	private HttpResponse<String> postAsDevice(String path, String json) throws Exception {
		return this.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path))
			.header("X-API-Key", ApiSession.DEVICE_API_KEY)
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(json))
			.build(), BodyHandlers.ofString());
	}

	private HttpResponse<String> get(int port, String path) throws Exception {
		return this.http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
				BodyHandlers.ofString());
	}

}
