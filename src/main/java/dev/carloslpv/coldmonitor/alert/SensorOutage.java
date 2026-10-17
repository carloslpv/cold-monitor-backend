package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A stretch with no readings from one equipment. It opens once the equipment has been silent for the configured time
 * and closes when the next reading arrives. Times are arrival times on the server clock, so a sensor with a wrong
 * clock is judged by when its readings actually reach us.
 */
@Entity
@Table(name = "sensor_outage")
public class SensorOutage {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipment_id", nullable = false, updatable = false)
	private Equipment equipment;

	@Column(name = "last_reading_at", nullable = false, updatable = false)
	private Instant lastReadingAt;

	@Column(name = "last_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal lastTemperature;

	@Column(name = "detected_at", nullable = false, updatable = false)
	private Instant detectedAt;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "end_temperature", precision = 5, scale = 2)
	private BigDecimal endTemperature;

	protected SensorOutage() {
	}

	SensorOutage(Equipment equipment, Instant lastReadingAt, BigDecimal lastTemperature, Instant detectedAt) {
		this.equipment = equipment;
		this.lastReadingAt = lastReadingAt;
		this.lastTemperature = lastTemperature;
		this.detectedAt = detectedAt;
	}

	/** The first reading after the silence arrived. */
	void end(Instant receivedAt, BigDecimal temperature) {
		this.endedAt = receivedAt;
		this.endTemperature = temperature;
	}

	/** Time without readings: up to now while open, up to the reading that ended it once closed. */
	public Duration silence(Instant now) {
		return Duration.between(this.lastReadingAt, isOpen() ? now : this.endedAt);
	}

	public boolean isOpen() {
		return this.endedAt == null;
	}

	public UUID getId() {
		return id;
	}

	public Equipment getEquipment() {
		return equipment;
	}

	public Instant getLastReadingAt() {
		return lastReadingAt;
	}

	public BigDecimal getLastTemperature() {
		return lastTemperature;
	}

	public Instant getDetectedAt() {
		return detectedAt;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BigDecimal getEndTemperature() {
		return endTemperature;
	}

}
