package dev.carloslpv.coldmonitor.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and logout themselves are handled by Spring Security (see {@link SecurityConfiguration}).
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

	/** Who is logged in; answers 401 without a session, which tells the dashboard to show its login form. */
	@GetMapping("/me")
	CurrentUser me(Authentication authentication) {
		return CurrentUser.from(authentication);
	}

}
