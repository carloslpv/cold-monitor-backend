package dev.carloslpv.coldmonitor.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

/**
 * Writes RFC 9457 problem details from security filters, which run before Spring MVC's exception handling.
 */
@Component
class ProblemWriter {

	private final JsonMapper json;

	ProblemWriter(JsonMapper json) {
		this.json = json;
	}

	void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		var problem = ProblemDetail.forStatusAndDetail(status, detail);
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		this.json.writeValue(response.getOutputStream(), problem);
	}

}
