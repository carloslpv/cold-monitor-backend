package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import dev.carloslpv.coldmonitor.reading.ReadingResponse;
import dev.carloslpv.coldmonitor.reading.ReadingStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlertMessagesTests {

	private static final Instant STARTED_AT = Instant.parse("2026-10-17T05:00:00Z");

	private final AlertMessages messages = new AlertMessages(ZoneId.of("America/Sao_Paulo"));

	@Test
	void describesAWarmExcursionStartInLocalTime() {
		var text = this.messages.format(alert(ExcursionAlert.Kind.STARTED, ReadingStatus.ABOVE_MAX, "9.30", null));

		assertThat(text).isEqualTo("""
				🔴 Temperatura acima do máximo
				Vaccine fridge (FRIDGE-01) · Pharmacy
				Leitura: 9,3 °C · faixa: 2,0 a 8,0 °C
				Início: 17/10/2026 02:00:00""");
	}

	@Test
	void describesTheEndWithPeakAndDuration() {
		var text = this.messages.format(alert(ExcursionAlert.Kind.ENDED, ReadingStatus.BELOW_MIN, "3.25",
				STARTED_AT.plus(Duration.ofMinutes(12).plusSeconds(30))));

		assertThat(text).isEqualTo("""
				✅ Temperatura de volta à faixa
				Vaccine fridge (FRIDGE-01) · Pharmacy
				Leitura: 3,25 °C · faixa: 2,0 a 8,0 °C
				Mínima: -1,5 °C · duração: 12 min 30 s
				Fim: 17/10/2026 02:12:30""");
	}

	@Test
	void leavesOutAMissingLocation() {
		var alert = alert(ExcursionAlert.Kind.STARTED, ReadingStatus.BELOW_MIN, "1.00", null);
		alert = new ExcursionAlert(alert.kind(), alert.excursionId(), alert.equipmentCode(), alert.equipmentName(),
				null, alert.status(), alert.temperature(), alert.peakTemperature(), alert.minTemperature(),
				alert.maxTemperature(), alert.startedAt(), alert.endedAt());

		assertThat(this.messages.format(alert)).contains("🔵 Temperatura abaixo do mínimo")
			.contains("\nVaccine fridge (FRIDGE-01)\n");
	}

	@Test
	void describesASilentSensor() {
		var text = this.messages.format(outage(SensorOutageAlert.Kind.STARTED, null, null));

		assertThat(text).isEqualTo("""
				⚠️ Sensor sem leituras
				Vaccine fridge (FRIDGE-01) · Pharmacy
				Última leitura: 4,8 °C em 17/10/2026 02:00:00
				Sem leituras há 5 min 15 s""");
	}

	@Test
	void warnsWhenTheSensorWentSilentMidExcursion() {
		var excursion = new SensorOutageAlert.OpenExcursion(ReadingStatus.ABOVE_MAX,
				STARTED_AT.minus(Duration.ofMinutes(3)), new BigDecimal("9.80"));

		assertThat(this.messages.format(outage(SensorOutageAlert.Kind.STARTED, null, excursion)))
			.endsWith("\nAtenção: estava acima do máximo desde 17/10/2026 01:57:00 · máxima: 9,8 °C");
	}

	@Test
	void describesReadingsComingBack() {
		var reading = new ReadingResponse(UUID.randomUUID(), UUID.randomUUID(), "FRIDGE-01", new BigDecimal("9.10"),
				STARTED_AT.plus(Duration.ofMinutes(42)), STARTED_AT.plus(Duration.ofMinutes(42)),
				ReadingStatus.ABOVE_MAX, new BigDecimal("2.00"), new BigDecimal("8.00"));

		assertThat(this.messages.format(outage(SensorOutageAlert.Kind.ENDED, reading, null))).isEqualTo("""
				🟢 Sensor voltou a enviar leituras
				Vaccine fridge (FRIDGE-01) · Pharmacy
				Leitura: 9,1 °C · faixa: 2,0 a 8,0 °C (fora da faixa)
				Sem leituras por 42 min
				Retorno: 17/10/2026 02:42:00""");
	}

	@Test
	void formatsDurations() {
		assertThat(AlertMessages.duration(Duration.ofSeconds(45))).isEqualTo("45 s");
		assertThat(AlertMessages.duration(Duration.ofMinutes(3))).isEqualTo("3 min");
		assertThat(AlertMessages.duration(Duration.ofMinutes(61))).isEqualTo("1 h 1 min");
		assertThat(AlertMessages.duration(Duration.ofHours(2))).isEqualTo("2 h");
	}

	private static SensorOutageAlert outage(SensorOutageAlert.Kind kind, ReadingResponse reading,
			SensorOutageAlert.OpenExcursion excursion) {
		return new SensorOutageAlert(kind, UUID.randomUUID(), UUID.randomUUID(), "FRIDGE-01", "Vaccine fridge",
				"Pharmacy", STARTED_AT, new BigDecimal("4.80"), STARTED_AT.plus(Duration.ofSeconds(315)),
				(reading != null) ? reading.receivedAt() : null, reading, excursion);
	}

	private static ExcursionAlert alert(ExcursionAlert.Kind kind, ReadingStatus status, String temperature,
			Instant endedAt) {
		return new ExcursionAlert(kind, UUID.randomUUID(), "FRIDGE-01", "Vaccine fridge", "Pharmacy", status,
				new BigDecimal(temperature), new BigDecimal("-1.50"), new BigDecimal("2.00"), new BigDecimal("8.00"),
				STARTED_AT, endedAt);
	}

}
