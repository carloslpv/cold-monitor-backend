package dev.carloslpv.coldmonitor.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

/**
 * JSON answers to login attempts, in place of the redirects form login sends by default.
 */
@Component
class AuthResponses {

	private static final Logger logger = LoggerFactory.getLogger(AuthResponses.class);

	private final JsonMapper json;

	private final ProblemWriter problems;

	AuthResponses(JsonMapper json, ProblemWriter problems) {
		this.json = json;
		this.problems = problems;
	}

	void loginSucceeded(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
			throws IOException {
		logger.info("User '{}' logged in from {}", authentication.getName(), request.getRemoteAddr());
		response.setStatus(HttpStatus.OK.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		this.json.writeValue(response.getOutputStream(), CurrentUser.from(authentication));
	}

	void loginFailed(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
			throws IOException {
		logger.warn("Failed login for '{}' from {}", printable(request.getParameter("username")),
				request.getRemoteAddr());
		this.problems.write(response, HttpStatus.UNAUTHORIZED, "Invalid username or password");
	}

	/** Keeps a user-supplied name from forging log lines. */
	private static String printable(String value) {
		return (value != null) ? value.replaceAll("\\p{Cntrl}", "?") : null;
	}

}
