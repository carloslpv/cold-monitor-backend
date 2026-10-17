package dev.carloslpv.coldmonitor.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/**
 * @param adminUsername user name of the dashboard and API administrator
 * @param adminPassword password of the administrator; required
 * @param deviceApiKey key that sensors send in the {@code X-API-Key} header to post readings; device access is off
 * while it is empty
 */
@ConfigurationProperties("cold-monitor.security")
public record SecurityProperties(
		@DefaultValue("admin") String adminUsername,
		String adminPassword,
		String deviceApiKey) {

	static final int MIN_PASSWORD_LENGTH = 8;

	static final int MIN_API_KEY_LENGTH = 16;

	public SecurityProperties {
		if (!StringUtils.hasText(adminUsername)) {
			throw new IllegalArgumentException("cold-monitor.security.admin-username (ADMIN_USERNAME) must not be blank");
		}
		if (adminPassword == null || adminPassword.length() < MIN_PASSWORD_LENGTH) {
			throw new IllegalArgumentException("Set ADMIN_PASSWORD (cold-monitor.security.admin-password) to at least "
					+ MIN_PASSWORD_LENGTH + " characters");
		}
		deviceApiKey = StringUtils.hasText(deviceApiKey) ? deviceApiKey.trim() : null;
		if (deviceApiKey != null && deviceApiKey.length() < MIN_API_KEY_LENGTH) {
			throw new IllegalArgumentException("DEVICE_API_KEY (cold-monitor.security.device-api-key) must have at least "
					+ MIN_API_KEY_LENGTH + " characters");
		}
	}

	boolean deviceAccessEnabled() {
		return deviceApiKey != null;
	}

	@Override
	public String toString() {
		// keeps secrets out of logs and failure analysis output
		return "SecurityProperties[adminUsername=" + adminUsername + ", deviceAccessEnabled=" + deviceAccessEnabled()
				+ "]";
	}

}
