package dev.carloslpv.coldmonitor.alert;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ExcursionAlertTests {

	private static final FakeTelegram telegram = FakeTelegram.start();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ExcursionRepository excursions;

	@DynamicPropertySource
	static void telegramProperties(DynamicPropertyRegistry registry) {
		registry.add("cold-monitor.alerts.telegram.api-url", telegram::url);
		registry.add("cold-monitor.alerts.telegram.bot-token", () -> "123:test-token");
		registry.add("cold-monitor.alerts.telegram.chat-id", () -> "4242");
	}

	@AfterAll
	static void stopTelegram() {
		telegram.stop();
	}

	@Test
	void sendsOneAlertWhenAnExcursionStartsAndOneWhenItEnds() throws Exception {
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		var start = Instant.now().minus(Duration.ofMinutes(30)).truncatedTo(ChronoUnit.SECONDS);
		var temperatures = List.of("5.00", "9.30", "10.10", "7.00", "9.00", "6.00", "5.50", "5.00");
		for (var i = 0; i < temperatures.size(); i++) {
			postReading(equipmentId, temperatures.get(i), start.plus(Duration.ofMinutes(i)));
		}

		var messages = telegram.awaitMessages(code, 2);
		assertThat(messages).allSatisfy((message) -> {
			assertThat(message.path()).isEqualTo("/bot123:test-token/sendMessage");
			assertThat(JsonPath.<String>read(message.body(), "$.chat_id")).isEqualTo("4242");
		});
		assertThat(messages.get(0).text()).startsWith("🔴 Temperatura acima do máximo")
			.contains("Leitura: 9,3 °C · faixa: 2,0 a 8,0 °C");
		// 7.00 started a recovery that 9.00 broke off; 6.00 and 5.50 finished it
		assertThat(messages.get(1).text()).startsWith("✅ Temperatura de volta à faixa")
			.contains("Leitura: 5,5 °C")
			.contains("Máxima: 10,1 °C · duração: 4 min");

		var excursion = this.excursions.findByEquipmentIdOrderByStartedAtAsc(equipmentId).getFirst();
		assertThat(this.excursions.findByEquipmentIdOrderByStartedAtAsc(equipmentId)).hasSize(1);
		assertThat(excursion.isOpen()).isFalse();
		assertThat(excursion.getStartedAt()).isEqualTo(start.plus(Duration.ofMinutes(1)));
		assertThat(excursion.getEndedAt()).isEqualTo(start.plus(Duration.ofMinutes(5)));
		assertThat(excursion.getPeakTemperature()).isEqualByComparingTo("10.10");

		// the 5.00 after the end changes nothing
		Thread.sleep(300);
		assertThat(telegram.messagesFor(code)).hasSize(2);
	}

	@Test
	void keepsTheExcursionOpenWhileTheTemperatureStaysOut() throws Exception {
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		var start = Instant.now().minus(Duration.ofMinutes(10)).truncatedTo(ChronoUnit.SECONDS);
		postReading(equipmentId, "1.50", start);
		postReading(equipmentId, "0.80", start.plusSeconds(60));
		postReading(equipmentId, "9.00", start.plusSeconds(120));

		assertThat(telegram.awaitMessages(code, 1).getFirst().text()).startsWith("🔵 Temperatura abaixo do mínimo");
		var excursion = this.excursions.findByEquipmentIdAndEndedAtIsNull(equipmentId).orElseThrow();
		assertThat(excursion.getLowestTemperature()).isEqualByComparingTo("0.80");
		assertThat(excursion.getHighestTemperature()).isEqualByComparingTo("9.00");
	}

	@Test
	void ignoresReadingsOlderThanTheLatestOne() throws Exception {
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		postReading(equipmentId, "5.00", now);
		postReading(equipmentId, "12.00", now.minus(Duration.ofMinutes(5)));

		assertThat(this.excursions.findByEquipmentIdOrderByStartedAtAsc(equipmentId)).isEmpty();
	}

	@Test
	void retriesWhenTelegramAsksToSlowDown() throws Exception {
		telegram.respondNextWith(429, """
				{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 1",
				 "parameters":{"retry_after":1}}""");
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		postReading(equipmentId, "11.00", Instant.now().truncatedTo(ChronoUnit.SECONDS));

		var attempts = telegram.awaitMessages(code, 2);
		assertThat(attempts.get(1).text()).isEqualTo(attempts.get(0).text());
	}

	private UUID registerFridge(String code) throws Exception {
		var body = this.mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON).content("""
				{ "code": "%s", "name": "Vaccine fridge", "location": "Pharmacy", "type": "REFRIGERATOR",
				  "minTemperature": 2.0, "maxTemperature": 8.0 }
				""".formatted(code)))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private void postReading(UUID equipmentId, String temperature, Instant recordedAt) throws Exception {
		this.mvc.perform(post("/api/v1/equipment/{id}/readings", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"temperature\": %s, \"recordedAt\": \"%s\" }".formatted(temperature, recordedAt)))
			.andExpect(status().isCreated());
	}

	private static String uniqueCode() {
		return "ALERT-" + UUID.randomUUID().toString().substring(0, 8);
	}

	/** Stands in for the Telegram Bot API and records every sendMessage call. */
	record FakeTelegram(HttpServer server, List<Message> messages, Queue<int[]> statuses, Queue<String> bodies) {

		static FakeTelegram start() {
			try {
				var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
				var telegram = new FakeTelegram(server, new CopyOnWriteArrayList<>(), new ConcurrentLinkedQueue<>(),
						new ConcurrentLinkedQueue<>());
				server.createContext("/", telegram::handle);
				server.start();
				return telegram;
			}
			catch (IOException ex) {
				throw new UncheckedIOException(ex);
			}
		}

		String url() {
			return "http://localhost:" + this.server.getAddress().getPort();
		}

		void respondNextWith(int status, String body) {
			this.statuses.add(new int[] { status });
			this.bodies.add(body);
		}

		List<Message> messagesFor(String equipmentCode) {
			return this.messages.stream().filter((message) -> message.text().contains(equipmentCode)).toList();
		}

		List<Message> awaitMessages(String equipmentCode, int count) {
			await().atMost(Duration.ofSeconds(10)).until(() -> messagesFor(equipmentCode).size() >= count);
			return messagesFor(equipmentCode);
		}

		void stop() {
			this.server.stop(0);
		}

		private void handle(HttpExchange exchange) throws IOException {
			var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			this.messages.add(new Message(exchange.getRequestURI().getPath(), body));
			var status = this.statuses.poll();
			var response = (status != null) ? this.bodies.poll() : "{\"ok\":true,\"result\":{}}";
			var bytes = response.getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders((status != null) ? status[0] : 200, bytes.length);
			try (var out = exchange.getResponseBody()) {
				out.write(bytes);
			}
		}

	}

	record Message(String path, String body) {

		String text() {
			return JsonPath.read(this.body, "$.text");
		}

	}

}
