package dev.carloslpv.coldmonitor.alert;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SensorOutageAlertTests {

	private static final FakeTelegram telegram = FakeTelegram.start();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private SensorOutageRepository outages;

	@DynamicPropertySource
	static void alertProperties(DynamicPropertyRegistry registry) {
		registry.add("cold-monitor.alerts.offline-after", () -> "2s");
		registry.add("cold-monitor.alerts.offline-check-interval", () -> "200ms");
		registry.add("cold-monitor.alerts.telegram.api-url", telegram::url);
		registry.add("cold-monitor.alerts.telegram.bot-token", () -> "123:test-token");
		registry.add("cold-monitor.alerts.telegram.chat-id", () -> "4242");
	}

	@AfterAll
	static void stopTelegram() {
		telegram.stop();
	}

	@Test
	void sendsOneAlertWhenASensorGoesSilentAndOneWhenItComesBack() throws Exception {
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		var neverReported = uniqueCode();
		registerFridge(neverReported);
		postReading(equipmentId, "5.00");

		Thread.sleep(1000);
		assertThat(telegram.messagesFor(code)).isEmpty();

		var offline = telegram.awaitMessages(code, 1).getFirst().text();
		assertThat(offline).startsWith("⚠️ Sensor sem leituras")
			.contains("Vaccine fridge (%s) · Pharmacy".formatted(code))
			.contains("Última leitura: 5,0 °C em ")
			.doesNotContain("Atenção");
		assertThat(openOutageCodes()).contains(code);

		// later checks do not repeat the alert while the sensor stays silent
		Thread.sleep(600);
		assertThat(telegram.messagesFor(code)).hasSize(1);

		postReading(equipmentId, "5.50");
		var online = telegram.awaitMessages(code, 2).get(1).text();
		assertThat(online).startsWith("🟢 Sensor voltou a enviar leituras")
			.contains("Leitura: 5,5 °C · faixa: 2,0 a 8,0 °C\n")
			.contains("Sem leituras por ");
		assertThat(openOutageCodes()).doesNotContain(code);

		var outage = this.outages.findByEquipmentIdOrderByDetectedAtAsc(equipmentId).getFirst();
		assertThat(outage.isOpen()).isFalse();
		assertThat(outage.getLastTemperature()).isEqualByComparingTo("5.00");
		assertThat(outage.getEndTemperature()).isEqualByComparingTo("5.50");
		assertThat(outage.getEndedAt()).isAfter(outage.getDetectedAt());

		// equipment that never sent a reading has nothing to lose
		assertThat(telegram.messagesFor(neverReported)).isEmpty();
	}

	@Test
	void mentionsAnExcursionThatWasStillOpen() throws Exception {
		var code = uniqueCode();
		var equipmentId = registerFridge(code);
		postReading(equipmentId, "11.00");

		var messages = telegram.awaitMessages(code, 2);
		assertThat(messages.get(0).text()).startsWith("🔴 Temperatura acima do máximo");
		assertThat(messages.get(1).text()).startsWith("⚠️ Sensor sem leituras")
			.contains("Atenção: estava acima do máximo desde ")
			.endsWith("máxima: 11,0 °C");
	}

	private List<String> openOutageCodes() throws Exception {
		var body = this.mvc.perform(get("/api/v1/sensor-outages").param("open", "true"))
			.andExpect(status().isOk())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$[*].equipmentCode");
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

	private void postReading(UUID equipmentId, String temperature) throws Exception {
		this.mvc.perform(post("/api/v1/equipment/{id}/readings", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{ \"temperature\": %s, \"recordedAt\": \"%s\" }".formatted(temperature,
					Instant.now().truncatedTo(ChronoUnit.MILLIS))))
			.andExpect(status().isCreated());
	}

	private static String uniqueCode() {
		return "SILENT-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
