package dev.carloslpv.coldmonitor.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Who may call what:
 * <ul>
 * <li>The administrator logs in with user name and password ({@code POST /api/v1/auth/login}) and gets a session
 * cookie, which also authorizes the live stream. State-changing requests from the browser carry a CSRF token read from
 * the {@code XSRF-TOKEN} cookie.</li>
 * <li>An optional viewer account logs in the same way but may only read, which suits a public demo.</li>
 * <li>Sensors send the device API key in {@code X-API-Key} and may only list and register equipment and post
 * readings.</li>
 * <li>The health endpoint stays public for container health checks.</li>
 * <li>Every actuator endpoint, metrics included, is open on the separate management port when one is set
 * ({@code MANAGEMENT_SERVER_PORT}, as the Docker image does). That port is never published or proxied, so only services
 * on the internal network, such as Prometheus, reach it. On the main port the other endpoints need the
 * administrator.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
class SecurityConfiguration {

	static final String ADMIN_ROLE = "ADMIN";

	static final String VIEWER_ROLE = "VIEWER";

	private static final Logger logger = LoggerFactory.getLogger(SecurityConfiguration.class);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProperties properties, ProblemWriter problems,
			AuthResponses responses, Environment environment) {
		if (!properties.deviceAccessEnabled()) {
			logger.warn("DEVICE_API_KEY is not set: sensors cannot send readings until it is");
		}
		var apiKeyFilter = new ApiKeyAuthenticationFilter(properties, problems);
		http.authorizeHttpRequests((requests) -> requests
			.requestMatchers(onManagementPort(environment)).permitAll()
			.requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
			.requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
			.requestMatchers(HttpMethod.GET, "/api/v1/equipment", "/api/v1/equipment/*")
				.hasAnyRole(ADMIN_ROLE, VIEWER_ROLE, ApiKeyAuthenticationFilter.DEVICE_ROLE)
			.requestMatchers(HttpMethod.POST, "/api/v1/equipment", "/api/v1/equipment/*/readings")
				.hasAnyRole(ADMIN_ROLE, ApiKeyAuthenticationFilter.DEVICE_ROLE)
			.requestMatchers(HttpMethod.GET, "/api/v1/**").hasAnyRole(ADMIN_ROLE, VIEWER_ROLE)
			.anyRequest().hasRole(ADMIN_ROLE))
			.addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
			.formLogin((form) -> form
				.loginPage("/api/v1/auth/login")
				.loginProcessingUrl("/api/v1/auth/login")
				.successHandler(responses::loginSucceeded)
				.failureHandler(responses::loginFailed))
			.logout((logout) -> logout
				.logoutUrl("/api/v1/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
			// the dashboard reads the token from the XSRF-TOKEN cookie and echoes it in X-XSRF-TOKEN; sensors use a
			// header a browser cannot attach cross-site, and never have a cookie to ride on
			.csrf((csrf) -> csrf.spa()
				.ignoringRequestMatchers((request) -> request.getHeader(ApiKeyAuthenticationFilter.HEADER) != null))
			.cors(Customizer.withDefaults())
			.requestCache((cache) -> cache.disable())
			.exceptionHandling((exceptions) -> exceptions
				.authenticationEntryPoint((request, response, ex) -> problems.write(response, HttpStatus.UNAUTHORIZED,
						"Authentication required"))
				.accessDeniedHandler((request, response, ex) -> problems.write(response, HttpStatus.FORBIDDEN,
						"Access denied")));
		return http.build();
	}

	/** Requests that reached the separate management server, when it runs. */
	private static RequestMatcher onManagementPort(Environment environment) {
		return (request) -> {
			// set once the servers have started; without a port of its own, the management port is an alias of the
			// main one
			var managementPort = environment.getProperty("local.management.port", Integer.class);
			var serverPort = environment.getProperty("local.server.port", Integer.class);
			return managementPort != null && !managementPort.equals(serverPort)
					&& request.getLocalPort() == managementPort;
		};
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	UserDetailsService userDetailsService(SecurityProperties properties, PasswordEncoder encoder) {
		var admin = User.withUsername(properties.adminUsername())
			.password(encoder.encode(properties.adminPassword()))
			.roles(ADMIN_ROLE)
			.build();
		var users = new InMemoryUserDetailsManager(admin);
		if (properties.viewerEnabled()) {
			users.createUser(User.withUsername(properties.viewerUsername())
				.password(encoder.encode(properties.viewerPassword()))
				.roles(VIEWER_ROLE)
				.build());
			logger.info("Read-only user '{}' enabled", properties.viewerUsername());
		}
		return users;
	}

}
