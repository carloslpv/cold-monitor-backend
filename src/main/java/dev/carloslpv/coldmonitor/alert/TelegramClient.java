package dev.carloslpv.coldmonitor.alert;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Minimal Telegram Bot API client that sends plain text messages to one chat.
 */
class TelegramClient {

	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

	private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

	private final RestClient http;

	private final String botToken;

	private final String chatId;

	TelegramClient(AlertProperties.Telegram properties) {
		var requestFactory = new JdkClientHttpRequestFactory(
				HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
		requestFactory.setReadTimeout(READ_TIMEOUT);
		this.http = RestClient.builder()
			.baseUrl(properties.apiUrl().toString())
			.requestFactory(requestFactory)
			.build();
		this.botToken = properties.botToken();
		this.chatId = properties.chatId();
	}

	void sendMessage(String text) {
		this.http.post()
			.uri("/bot{token}/sendMessage", this.botToken)
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("chat_id", this.chatId, "text", text))
			.retrieve()
			.toBodilessEntity();
	}

	/** How long Telegram asked us to wait after a 429, when it says. */
	static Optional<Duration> retryAfter(HttpClientErrorException.TooManyRequests ex) {
		try {
			var error = ex.getResponseBodyAs(Error.class);
			if (error != null && error.parameters() != null && error.parameters().retryAfter() != null) {
				return Optional.of(Duration.ofSeconds(error.parameters().retryAfter()));
			}
		}
		catch (RuntimeException ignored) {
			// fall back to the default delay
		}
		return Optional.empty();
	}

	/** The token is part of every request URL, so error messages are scrubbed before they reach the log. */
	String redact(String message) {
		return (message != null) ? message.replace(this.botToken, "<token>") : null;
	}

	record Error(String description, Parameters parameters) {

		record Parameters(@JsonProperty("retry_after") Integer retryAfter) {
		}

	}

}
