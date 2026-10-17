package dev.carloslpv.coldmonitor.security;

import com.jayway.jsonpath.JsonPath;
import dev.carloslpv.coldmonitor.ApiSession;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccessControlTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void requiresAuthenticationForTheApi() throws Exception {
		mvc.perform(get("/api/v1/equipment"))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.detail").value("Authentication required"));
		mvc.perform(get("/api/v1/readings/stream")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/equipment").with(csrf()).contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("ANON-01"))).andExpect(status().isUnauthorized());
	}

	@Test
	void keepsTheHealthCheckPublic() throws Exception {
		mvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	void letsDevicesRegisterEquipmentAndSendReadings() throws Exception {
		var body = mvc.perform(device(post("/api/v1/equipment")).contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("DEVICE-01")))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(device(get("/api/v1/equipment"))).andExpect(status().isOk());
		mvc.perform(device(get("/api/v1/equipment/{id}", id))).andExpect(status().isOk());
		mvc.perform(device(post("/api/v1/equipment/{id}/readings", id)).contentType(MediaType.APPLICATION_JSON)
			.content("{\"temperature\": 4.2}")).andExpect(status().isCreated());
	}

	@Test
	void keepsDevicesAwayFromEverythingElse() throws Exception {
		var body = mvc.perform(device(post("/api/v1/equipment")).contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("DEVICE-02"))).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.id");

		mvc.perform(device(put("/api/v1/equipment/{id}", id)).contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("DEVICE-02")))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.detail").value("Access denied"));
		mvc.perform(device(get("/api/v1/equipment/{id}/readings", id))).andExpect(status().isForbidden());
		mvc.perform(device(get("/api/v1/readings/out-of-range"))).andExpect(status().isForbidden());
		mvc.perform(device(get("/api/v1/sensor-outages"))).andExpect(status().isForbidden());
		mvc.perform(device(get("/api/v1/readings/stream"))).andExpect(status().isForbidden());
		mvc.perform(device(get("/api/v1/auth/me"))).andExpect(status().isForbidden());
	}

	@Test
	void rejectsAWrongApiKey() throws Exception {
		mvc.perform(get("/api/v1/equipment").header("X-API-Key", "not-the-device-key-at-all"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.detail").value("Invalid API key"));
	}

	@Test
	void requiresACsrfTokenForBrowserWrites() throws Exception {
		mvc.perform(post("/api/v1/equipment").with(user("admin").roles("ADMIN"))
			.contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("CSRF-01"))).andExpect(status().isForbidden());
		mvc.perform(post("/api/v1/equipment").with(user("admin").roles("ADMIN")).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content(equipmentJson("CSRF-01"))).andExpect(status().isCreated());
	}

	private static MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder request) {
		return request.header("X-API-Key", ApiSession.DEVICE_API_KEY);
	}

	private static String equipmentJson(String code) {
		return """
				{"code": "%s", "name": "Fridge %s", "type": "REFRIGERATOR", "minTemperature": 2.0, "maxTemperature": 8.0}
				""".formatted(code, code);
	}

}
