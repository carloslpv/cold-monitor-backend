package dev.carloslpv.coldmonitor.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates sensors by the {@code X-API-Key} header. The authentication lasts for the request only: devices never
 * get a session. A request with a wrong key is rejected right away instead of falling through as anonymous.
 */
class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";

	static final String DEVICE_ROLE = "DEVICE";

	static final String DEVICE_PRINCIPAL = "device";

	private final byte[] expectedKey;

	private final ProblemWriter problems;

	ApiKeyAuthenticationFilter(SecurityProperties properties, ProblemWriter problems) {
		this.expectedKey = properties.deviceAccessEnabled()
				? properties.deviceApiKey().getBytes(StandardCharsets.UTF_8) : null;
		this.problems = problems;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		var key = request.getHeader(HEADER);
		if (key == null) {
			chain.doFilter(request, response);
			return;
		}
		if (!matches(key)) {
			this.problems.write(response, HttpStatus.UNAUTHORIZED, "Invalid API key");
			return;
		}
		var authentication = UsernamePasswordAuthenticationToken.authenticated(DEVICE_PRINCIPAL, null,
				List.of(new SimpleGrantedAuthority("ROLE_" + DEVICE_ROLE)));
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		try {
			chain.doFilter(request, response);
		}
		finally {
			SecurityContextHolder.clearContext();
		}
	}

	private boolean matches(String key) {
		// constant-time comparison, so response timing does not leak how much of a guess was right
		return this.expectedKey != null
				&& MessageDigest.isEqual(this.expectedKey, key.trim().getBytes(StandardCharsets.UTF_8));
	}

}
