package dev.carloslpv.coldmonitor.reading;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.jayway.jsonpath.JsonPath;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ReadingStreamTests {

	private static final String DASHBOARD_ORIGIN = "http://localhost:5173";

	private final HttpClient http = HttpClient.newHttpClient();

	@LocalServerPort
	private int port;

	@Autowired
	private ReadingStream stream;

	@Test
	void streamsEveryStoredReadingToSubscribers() throws Exception {
		var equipmentId = registerFridge("STREAM-01");
		var before = stream.subscriberCount();

		HttpResponse<Stream<String>> response = http
			.sendAsync(HttpRequest.newBuilder(uri("/api/v1/readings/stream")).GET().build(), BodyHandlers.ofLines())
			.get(10, TimeUnit.SECONDS);
		try (var lines = response.body()) {
			assertThat(response.statusCode()).isEqualTo(200);
			assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
					(type) -> assertThat(type).startsWith("text/event-stream"));
			await().atMost(Duration.ofSeconds(5)).until(() -> stream.subscriberCount() > before);

			post("/api/v1/equipment/" + equipmentId + "/readings", "{\"temperature\": 4.5}");
			post("/api/v1/equipment/" + equipmentId + "/readings", "{\"temperature\": 9.3}");

			var events = lines.iterator();
			var first = nextReading(events);
			assertThat(JsonPath.<String>read(first, "$.equipmentId")).isEqualTo(equipmentId);
			assertThat(JsonPath.<String>read(first, "$.status")).isEqualTo("IN_RANGE");
			var second = nextReading(events);
			assertThat(JsonPath.<String>read(second, "$.status")).isEqualTo("ABOVE_MAX");
			assertThat(JsonPath.<Double>read(second, "$.temperature")).isEqualTo(9.3);
			assertThat(JsonPath.<Double>read(second, "$.maxTemperature")).isEqualTo(8.0);
		}
	}

	@Test
	void allowsTheDashboardOrigin() throws Exception {
		var preflight = http.send(HttpRequest.newBuilder(uri("/api/v1/equipment"))
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
			.header("Origin", DASHBOARD_ORIGIN)
			.header("Access-Control-Request-Method", "GET")
			.build(), BodyHandlers.discarding());

		assertThat(preflight.statusCode()).isEqualTo(200);
		assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).hasValue(DASHBOARD_ORIGIN);
	}

	@Test
	void rejectsUnknownOrigins() throws Exception {
		var preflight = http.send(HttpRequest.newBuilder(uri("/api/v1/equipment"))
			.method("OPTIONS", HttpRequest.BodyPublishers.noBody())
			.header("Origin", "http://evil.example")
			.header("Access-Control-Request-Method", "GET")
			.build(), BodyHandlers.discarding());

		assertThat(preflight.statusCode()).isEqualTo(403);
	}

	/** Reads lines until the next {@code reading} event and returns its JSON payload. */
	private static String nextReading(Iterator<String> lines) {
		var inReading = false;
		while (lines.hasNext()) {
			var line = lines.next();
			if (line.equals("event:reading")) {
				inReading = true;
			}
			else if (inReading && line.startsWith("data:")) {
				return line.substring("data:".length());
			}
		}
		throw new AssertionError("Stream ended before a reading event arrived");
	}

	private String registerFridge(String code) throws Exception {
		var body = """
				{"code": "%s", "name": "Stream fridge", "type": "REFRIGERATOR",
				 "minTemperature": 2.0, "maxTemperature": 8.0}
				""".formatted(code);
		return JsonPath.read(post("/api/v1/equipment", body), "$.id");
	}

	private String post(String path, String json) throws Exception {
		var response = http.send(HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "application/json")
			.POST(BodyPublishers.ofString(json))
			.timeout(Duration.ofSeconds(10))
			.build(), BodyHandlers.ofString());
		assertThat(response.statusCode()).isEqualTo(201);
		return response.body();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + port + path);
	}

}
