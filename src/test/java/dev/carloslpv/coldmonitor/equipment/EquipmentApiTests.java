package dev.carloslpv.coldmonitor.equipment;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipmentApiTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void registersAndFetchesEquipment() throws Exception {
		var code = uniqueCode();
		var body = mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(code, "2.00", "8.00")))
			.andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/equipment/")))
			.andExpect(jsonPath("$.code").value(code))
			.andExpect(jsonPath("$.minTemperature").value(2.0))
			.andExpect(jsonPath("$.maxTemperature").value(8.0))
			.andExpect(jsonPath("$.active").value(true))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(get("/api/v1/equipment/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Vaccine fridge " + code))
			.andExpect(jsonPath("$.type").value("REFRIGERATOR"));
	}

	@Test
	void updatesThresholds() throws Exception {
		var code = uniqueCode();
		String id = JsonPath.read(mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(code, "2.00", "8.00"))).andReturn().getResponse().getContentAsString(), "$.id");

		mvc.perform(put("/api/v1/equipment/{id}", id).contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(code, "-25.00", "-15.00")))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.minTemperature").value(-25.0))
			.andExpect(jsonPath("$.maxTemperature").value(-15.0));
	}

	@Test
	void rejectsMinNotLowerThanMax() throws Exception {
		mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(uniqueCode(), "8.00", "2.00")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.thresholdRangeValid").value("minTemperature must be lower than maxTemperature"));
	}

	@Test
	void rejectsMissingFields() throws Exception {
		mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors.code").exists())
			.andExpect(jsonPath("$.errors.minTemperature").exists());
	}

	@Test
	void rejectsDuplicateCode() throws Exception {
		var code = uniqueCode();
		mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(code, "2.00", "8.00"))).andExpect(status().isCreated());

		mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson(code, "2.00", "8.00")))
			.andExpect(status().isConflict());
	}

	@Test
	void returnsNotFoundForUnknownEquipment() throws Exception {
		mvc.perform(get("/api/v1/equipment/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private static String uniqueCode() {
		return "EQ-" + UUID.randomUUID().toString().substring(0, 8);
	}

	private static String equipmentJson(String code, String min, String max) {
		return """
				{
				  "code": "%s",
				  "name": "Vaccine fridge %s",
				  "location": "Pharmacy",
				  "type": "REFRIGERATOR",
				  "minTemperature": %s,
				  "maxTemperature": %s
				}
				""".formatted(code, code, min, max);
	}

}
