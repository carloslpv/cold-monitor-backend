package dev.carloslpv.coldmonitor;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * An HTTP client against a running test server that behaves like the dashboard: it keeps cookies and echoes the
 * {@code XSRF-TOKEN} cookie in the {@code X-XSRF-TOKEN} header of state-changing requests.
 */
public final class ApiSession {

	public static final String ADMIN_USERNAME = "admin";

	public static final String ADMIN_PASSWORD = "test-admin-password";

	public static final String DEVICE_API_KEY = "test-device-api-key-0123456789";

	private static final Duration TIMEOUT = Duration.ofSeconds(10);

	private final int port;

	private final CookieManager cookies = new CookieManager();

	private final HttpClient http;

	private ApiSession(int port) {
		this.port = port;
		this.http = HttpClient.newBuilder().cookieHandler(this.cookies).build();
	}

	/** A client with no session yet. */
	public static ApiSession anonymous(int port) {
		return new ApiSession(port);
	}

	/** A client logged in as the test administrator. */
	public static ApiSession admin(int port) throws IOException, InterruptedException {
		var session = new ApiSession(port);
		var response = session.login(ADMIN_USERNAME, ADMIN_PASSWORD);
		if (response.statusCode() != 200) {
			throw new IllegalStateException("Login failed with " + response.statusCode() + ": " + response.body());
		}
		return session;
	}

	public HttpResponse<String> login(String username, String password) throws IOException, InterruptedException {
		// the first request hands out the CSRF cookie
		get("/api/v1/auth/me");
		var form = "username=" + encode(username) + "&password=" + encode(password);
		return send(request("/api/v1/auth/login").header("Content-Type", "application/x-www-form-urlencoded")
			.POST(BodyPublishers.ofString(form)));
	}

	public HttpResponse<String> get(String path) throws IOException, InterruptedException {
		return send(request(path).GET());
	}

	public HttpResponse<String> post(String path, String json) throws IOException, InterruptedException {
		return send(request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(json)));
	}

	/** Builds a request with the session cookie and CSRF header, for callers that need their own body handler. */
	public HttpRequest.Builder request(String path) {
		var builder = HttpRequest.newBuilder(uri(path)).timeout(TIMEOUT);
		var token = xsrfToken();
		if (token != null) {
			builder.header("X-XSRF-TOKEN", token);
		}
		return builder;
	}

	public HttpClient client() {
		return this.http;
	}

	public URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	public String xsrfToken() {
		return this.cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter((cookie) -> cookie.getName().equals("XSRF-TOKEN"))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElse(null);
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
		return this.http.send(request.build(), BodyHandlers.ofString());
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
