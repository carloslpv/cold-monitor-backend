package dev.carloslpv.coldmonitor.alert;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * @param recoveryReadings consecutive in-range readings needed to close an excursion
 * @param offlineAfter how long an equipment may go without readings before it counts as offline
 * @param offlineCheckInterval how often equipment is checked for missing readings
 * @param timeZone zone used for the times written in alert messages
 */
@ConfigurationProperties("cold-monitor.alerts")
public record AlertProperties(
		@DefaultValue("2") int recoveryReadings,
		@DefaultValue("5m") Duration offlineAfter,
		@DefaultValue("15s") Duration offlineCheckInterval,
		@DefaultValue("America/Sao_Paulo") ZoneId timeZone,
		@DefaultValue Telegram telegram) {

	public AlertProperties {
		if (recoveryReadings < 1) {
			throw new IllegalArgumentException("cold-monitor.alerts.recovery-readings must be at least 1");
		}
		if (!offlineAfter.isPositive()) {
			throw new IllegalArgumentException("cold-monitor.alerts.offline-after must be positive");
		}
		if (!offlineCheckInterval.isPositive()) {
			throw new IllegalArgumentException("cold-monitor.alerts.offline-check-interval must be positive");
		}
	}

	/**
	 * Telegram Bot API settings. Alerts are only logged while the token or the chat id is missing.
	 */
	public record Telegram(String botToken, String chatId, @DefaultValue("https://api.telegram.org") URI apiUrl) {

		public boolean isConfigured() {
			return StringUtils.hasText(this.botToken) && StringUtils.hasText(this.chatId);
		}

		@Override
		public String toString() {
			// keep the token out of logs and error reports
			return "Telegram[chatId=%s, apiUrl=%s, botToken=%s]".formatted(this.chatId, this.apiUrl,
					StringUtils.hasText(this.botToken) ? "***" : "");
		}

	}

}
