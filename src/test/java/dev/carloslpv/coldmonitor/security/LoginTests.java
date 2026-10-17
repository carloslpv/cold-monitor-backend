package dev.carloslpv.coldmonitor.security;

import java.util.List;

import com.jayway.jsonpath.JsonPath;
import dev.carloslpv.coldmonitor.ApiSession;
import dev.carloslpv.coldmonitor.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics
@Import(TestcontainersConfiguration.class)
class LoginTests {

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcClient jdbc;

	@Test
	void logsInAndOut() throws Exception {
		var session = ApiSession.anonymous(port);
		assertThat(session.get("/api/v1/auth/me").statusCode()).isEqualTo(401);

		var login = session.login(ApiSession.ADMIN_USERNAME, ApiSession.ADMIN_PASSWORD);
		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(login.body(), "$.username")).isEqualTo("admin");

		var me = session.get("/api/v1/auth/me");
		assertThat(me.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(me.body(), "$.username")).isEqualTo("admin");
		assertThat(session.get("/api/v1/equipment").statusCode()).isEqualTo(200);

		var logout = session.post("/api/v1/auth/logout", "");
		assertThat(logout.statusCode()).isEqualTo(204);
		assertThat(session.get("/api/v1/auth/me").statusCode()).isEqualTo(401);
	}

	@Test
	void keepsMetricsBehindTheAdminLoginOnTheMainPort() throws Exception {
		assertThat(ApiSession.anonymous(port).get("/actuator/prometheus").statusCode()).isEqualTo(401);
		assertThat(ApiSession.admin(port).get("/actuator/prometheus").statusCode()).isEqualTo(200);
	}

	@Test
	void logsInTheViewer() throws Exception {
		var session = ApiSession.anonymous(port);

		var login = session.login(ApiSession.VIEWER_USERNAME, ApiSession.VIEWER_PASSWORD);

		assertThat(login.statusCode()).isEqualTo(200);
		assertThat(JsonPath.<String>read(login.body(), "$.username")).isEqualTo("viewer");
		assertThat(JsonPath.<List<String>>read(login.body(), "$.roles")).containsExactly("VIEWER");
		assertThat(session.get("/api/v1/equipment").statusCode()).isEqualTo(200);
		assertThat(session.post("/api/v1/auth/logout", "").statusCode()).isEqualTo(204);
	}

	@Test
	void rejectsAWrongPassword() throws Exception {
		var session = ApiSession.anonymous(port);

		var login = session.login(ApiSession.ADMIN_USERNAME, "wrong-password");

		assertThat(login.statusCode()).isEqualTo(401);
		assertThat(JsonPath.<String>read(login.body(), "$.detail")).isEqualTo("Invalid username or password");
		assertThat(session.get("/api/v1/equipment").statusCode()).isEqualTo(401);
	}

	@Test
	void writesWithTheSessionAndCsrfToken() throws Exception {
		var session = ApiSession.admin(port);

		var created = session.post("/api/v1/equipment", """
				{"code": "LOGIN-01", "name": "Login fridge", "type": "REFRIGERATOR",
				 "minTemperature": 2.0, "maxTemperature": 8.0}
				""");

		assertThat(created.statusCode()).isEqualTo(201);
	}

	@Test
	void keepsSessionsInTheDatabase() throws Exception {
		ApiSession.admin(port);

		var stored = jdbc.sql("SELECT count(*) FROM spring_session WHERE principal_name = 'admin'")
			.query(Long.class)
			.single();

		assertThat(stored).isPositive();
	}

}
