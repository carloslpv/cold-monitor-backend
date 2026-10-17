package dev.carloslpv.coldmonitor.security;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * The logged-in user as returned by {@code /api/v1/auth/login} and {@code /api/v1/auth/me}.
 */
public record CurrentUser(String username, List<String> roles) {

	static CurrentUser from(Authentication authentication) {
		var roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter((authority) -> authority.startsWith("ROLE_"))
			.map((authority) -> authority.substring("ROLE_".length()))
			.toList();
		return new CurrentUser(authentication.getName(), roles);
	}

}
