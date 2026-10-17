package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.StringJoiner;

import dev.carloslpv.coldmonitor.reading.ReadingStatus;

import org.springframework.util.StringUtils;

/**
 * Writes alert texts in Brazilian Portuguese, the language of the people receiving them. Plain text only, so
 * equipment names never need escaping.
 */
class AlertMessages {

	private static final Locale PT_BR = Locale.of("pt", "BR");

	private final DateTimeFormatter timeFormat;

	AlertMessages(ZoneId zone) {
		this.timeFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", PT_BR).withZone(zone);
	}

	String format(ExcursionAlert alert) {
		var text = new StringJoiner("\n");
		if (alert.kind() == ExcursionAlert.Kind.STARTED) {
			text.add((alert.status() == ReadingStatus.ABOVE_MAX) ? "🔴 Temperatura acima do máximo"
					: "🔵 Temperatura abaixo do mínimo");
			text.add(equipment(alert));
			text.add("Leitura: %s · faixa: %s".formatted(celsius(alert.temperature()), range(alert)));
			text.add("Início: " + this.timeFormat.format(alert.startedAt()));
		}
		else {
			text.add("✅ Temperatura de volta à faixa");
			text.add(equipment(alert));
			text.add("Leitura: %s · faixa: %s".formatted(celsius(alert.temperature()), range(alert)));
			text.add("%s: %s · duração: %s".formatted(
					(alert.status() == ReadingStatus.ABOVE_MAX) ? "Máxima" : "Mínima",
					celsius(alert.peakTemperature()), duration(Duration.between(alert.startedAt(), alert.endedAt()))));
			text.add("Fim: " + this.timeFormat.format(alert.endedAt()));
		}
		return text.toString();
	}

	private static String equipment(ExcursionAlert alert) {
		var line = "%s (%s)".formatted(alert.equipmentName(), alert.equipmentCode());
		return StringUtils.hasText(alert.equipmentLocation()) ? line + " · " + alert.equipmentLocation() : line;
	}

	private static String range(ExcursionAlert alert) {
		return "%s a %s".formatted(number(alert.minTemperature()), celsius(alert.maxTemperature()));
	}

	private static String celsius(BigDecimal value) {
		return number(value) + " °C";
	}

	private static String number(BigDecimal value) {
		// DecimalFormat is not thread safe, so each call gets its own
		return new DecimalFormat("0.0#", DecimalFormatSymbols.getInstance(PT_BR)).format(value);
	}

	static String duration(Duration duration) {
		var seconds = Math.max(0, duration.toSeconds());
		if (seconds < 60) {
			return seconds + " s";
		}
		var minutes = seconds / 60;
		if (minutes < 60) {
			var rest = seconds % 60;
			return (rest == 0) ? minutes + " min" : "%d min %d s".formatted(minutes, rest);
		}
		var hours = minutes / 60;
		var rest = minutes % 60;
		return (rest == 0) ? hours + " h" : "%d h %d min".formatted(hours, rest);
	}

}
