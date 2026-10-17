package dev.carloslpv.coldmonitor;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Makes every MockMvc request run as the logged-in administrator with a valid CSRF token, for tests about the API
 * itself rather than about access control.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AdminMockMvcConfiguration {

	@Bean
	MockMvcBuilderCustomizer adminByDefault() {
		return (builder) -> builder.defaultRequest(get("/").with(user("admin").roles("ADMIN")).with(csrf()));
	}

}
