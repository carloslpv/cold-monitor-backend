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

	String format(SensorOutageAlert alert) {
		var text = new StringJoiner("\n");
		var equipment = equipment(alert.equipmentName(), alert.equipmentCode(), alert.equipmentLocation());
		if (alert.kind() == SensorOutageAlert.Kind.STARTED) {
			text.add("⚠️ Sensor sem leituras");
			text.add(equipment);
			text.add("Última leitura: %s em %s".formatted(celsius(alert.lastTemperature()),
					this.timeFormat.format(alert.lastReadingAt())));
			text.add("Sem leituras há " + duration(Duration.between(alert.lastReadingAt(), alert.detectedAt())));
			var excursion = alert.excursion();
			if (excursion != null) {
				text.add("Atenção: estava %s desde %s · %s: %s".formatted(
						(excursion.status() == ReadingStatus.ABOVE_MAX) ? "acima do máximo" : "abaixo do mínimo",
						this.timeFormat.format(excursion.startedAt()),
						(excursion.status() == ReadingStatus.ABOVE_MAX) ? "máxima" : "mínima",
						celsius(excursion.peakTemperature())));
			}
		}
		else {
			var reading = alert.reading();
			text.add("🟢 Sensor voltou a enviar leituras");
			text.add(equipment);
			text.add("Leitura: %s · faixa: %s%s".formatted(celsius(reading.temperature()),
					range(reading.minTemperature(), reading.maxTemperature()),
					reading.status().isOutOfRange() ? " (fora da faixa)" : ""));
			text.add("Sem leituras por " + duration(Duration.between(alert.lastReadingAt(), alert.endedAt())));
			text.add("Retorno: " + this.timeFormat.format(alert.endedAt()));
		}
		return text.toString();
	}

	private static String equipment(ExcursionAlert alert) {
		return equipment(alert.equipmentName(), alert.equipmentCode(), alert.equipmentLocation());
	}

	private static String equipment(String name, String code, String location) {
		var line = "%s (%s)".formatted(name, code);
		return StringUtils.hasText(location) ? line + " · " + location : line;
	}

	private static String range(ExcursionAlert alert) {
		return range(alert.minTemperature(), alert.maxTemperature());
	}

	private static String range(BigDecimal min, BigDecimal max) {
		return "%s a %s".formatted(number(min), celsius(max));
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
