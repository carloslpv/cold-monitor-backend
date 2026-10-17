package dev.carloslpv.coldmonitor.reading;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class ReadingApiTests {

	@Autowired
	private MockMvc mvc;

	@Autowired
	private ApplicationEvents events;

	@Test
	void storesInRangeReadingWithoutRaisingEvent() throws Exception {
		var equipmentId = registerFridge();

		mvc.perform(postReading(equipmentId, "4.50", null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("IN_RANGE"))
			.andExpect(jsonPath("$.temperature").value(4.5))
			.andExpect(jsonPath("$.minTemperature").value(2.0))
			.andExpect(jsonPath("$.maxTemperature").value(8.0))
			.andExpect(jsonPath("$.recordedAt").exists());

		assertThat(events.stream(OutOfRangeReadingEvent.class)).isEmpty();
	}

	@Test
	void flagsOutOfRangeReadingsAndPublishesEvent() throws Exception {
		var equipmentId = registerFridge();

		mvc.perform(postReading(equipmentId, "9.30", null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("ABOVE_MAX"));
		mvc.perform(postReading(equipmentId, "1.20", null))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("BELOW_MIN"));

		assertThat(events.stream(OutOfRangeReadingEvent.class))
			.extracting(OutOfRangeReadingEvent::status)
			.containsExactly(ReadingStatus.ABOVE_MAX, ReadingStatus.BELOW_MIN);
		assertThat(events.stream(OutOfRangeReadingEvent.class))
			.allSatisfy((event) -> assertThat(event.equipmentId()).isEqualTo(UUID.fromString(equipmentId)));
	}

	@Test
	void listsOnlyOutOfRangeReadingsForEquipment() throws Exception {
		var equipmentId = registerFridge();
		mvc.perform(postReading(equipmentId, "5.00", null)).andExpect(status().isCreated());
		mvc.perform(postReading(equipmentId, "12.00", null)).andExpect(status().isCreated());

		mvc.perform(get("/api/v1/readings/out-of-range").param("equipmentId", equipmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(1)))
			.andExpect(jsonPath("$.content[0].temperature").value(12.0))
			.andExpect(jsonPath("$.content[0].status").value("ABOVE_MAX"))
			.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void returnsHistoryNewestFirstWithinWindow() throws Exception {
		var equipmentId = registerFridge();
		var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
		mvc.perform(postReading(equipmentId, "3.00", now.minus(Duration.ofHours(2))));
		mvc.perform(postReading(equipmentId, "4.00", now.minus(Duration.ofHours(1))));
		mvc.perform(postReading(equipmentId, "5.00", now.minus(Duration.ofDays(3))));

		mvc.perform(get("/api/v1/equipment/{id}/readings", equipmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content", hasSize(2)))
			.andExpect(jsonPath("$.content[0].temperature").value(4.0))
			.andExpect(jsonPath("$.content[1].temperature").value(3.0));

		mvc.perform(get("/api/v1/equipment/{id}/readings", equipmentId)
			.param("from", now.minus(Duration.ofDays(4)).toString())
			.param("to", now.toString()))
			.andExpect(jsonPath("$.content", hasSize(3)));

		mvc.perform(get("/api/v1/equipment/{id}/readings/latest", equipmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.temperature").value(4.0));
	}

	@Test
	void keepsThresholdsInForceWhenReadingArrived() throws Exception {
		var equipmentId = registerFridge();
		mvc.perform(postReading(equipmentId, "9.00", null)).andExpect(jsonPath("$.status").value("ABOVE_MAX"));

		mvc.perform(put("/api/v1/equipment/{id}", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content(fridgeJson("FRIDGE-" + UUID.randomUUID(), "0.00", "10.00")))
			.andExpect(status().isOk());

		mvc.perform(get("/api/v1/equipment/{id}/readings/latest", equipmentId))
			.andExpect(jsonPath("$.status").value("ABOVE_MAX"))
			.andExpect(jsonPath("$.maxTemperature").value(8.0));
		mvc.perform(postReading(equipmentId, "9.00", null)).andExpect(jsonPath("$.status").value("IN_RANGE"));
	}

	@Test
	void rejectsInvalidReadings() throws Exception {
		var equipmentId = registerFridge();

		mvc.perform(post("/api/v1/equipment/{id}/readings", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.temperature").exists());
		mvc.perform(postReading(equipmentId, "4.00", Instant.now().plus(Duration.ofHours(1))))
			.andExpect(status().isBadRequest());
		mvc.perform(postReading(UUID.randomUUID().toString(), "4.00", null)).andExpect(status().isNotFound());
	}

	@Test
	void rejectsReadingsForInactiveEquipment() throws Exception {
		var code = "FRIDGE-" + UUID.randomUUID();
		var equipmentId = registerFridge(code);
		mvc.perform(put("/api/v1/equipment/{id}", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content(fridgeJson(code, "2.00", "8.00").replace("}", ", \"active\": false }")))
			.andExpect(status().isOk());

		mvc.perform(postReading(equipmentId, "4.00", null)).andExpect(status().isConflict());
	}

	@Test
	void returnsNotFoundWhenNoReadingYet() throws Exception {
		mvc.perform(get("/api/v1/equipment/{id}/readings/latest", registerFridge())).andExpect(status().isNotFound());
	}

	private String registerFridge() throws Exception {
		return registerFridge("FRIDGE-" + UUID.randomUUID());
	}

	private String registerFridge(String code) throws Exception {
		var body = mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(fridgeJson(code, "2.00", "8.00")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String fridgeJson(String code, String min, String max) {
		return """
				{ "code": "%s", "name": "Vaccine fridge", "type": "REFRIGERATOR",
				  "minTemperature": %s, "maxTemperature": %s }
				""".formatted(code, min, max);
	}

	private static RequestBuilder postReading(String equipmentId,
			String temperature, Instant recordedAt) {
		var json = (recordedAt != null)
				? "{ \"temperature\": %s, \"recordedAt\": \"%s\" }".formatted(temperature, recordedAt)
				: "{ \"temperature\": %s }".formatted(temperature);
		return post("/api/v1/equipment/{id}/readings", equipmentId).contentType(MediaType.APPLICATION_JSON)
			.content(json);
	}

}
