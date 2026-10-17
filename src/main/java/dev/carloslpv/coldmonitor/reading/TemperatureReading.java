package dev.carloslpv.coldmonitor.reading;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.equipment.Equipment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single temperature sample sent by a sensor. The equipment thresholds in force at ingestion are copied onto the
 * reading so that its status stays explainable after the thresholds change.
 */
@Entity
@Table(name = "temperature_reading")
public class TemperatureReading {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipment_id", nullable = false, updatable = false)
	private Equipment equipment;

	@Column(nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal temperature;

	@Column(name = "recorded_at", nullable = false, updatable = false)
	private Instant recordedAt;

	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ReadingStatus status;

	@Column(name = "min_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal minTemperature;

	@Column(name = "max_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal maxTemperature;

	protected TemperatureReading() {
	}

	public TemperatureReading(Equipment equipment, BigDecimal temperature, Instant recordedAt, Instant receivedAt) {
		this.equipment = equipment;
		this.temperature = temperature;
		this.recordedAt = recordedAt;
		this.receivedAt = receivedAt;
		this.minTemperature = equipment.getMinTemperature();
		this.maxTemperature = equipment.getMaxTemperature();
		this.status = ReadingStatus.classify(temperature, this.minTemperature, this.maxTemperature);
	}

	public UUID getId() {
		return id;
	}

	public Equipment getEquipment() {
		return equipment;
	}

	public BigDecimal getTemperature() {
		return temperature;
	}

	public Instant getRecordedAt() {
		return recordedAt;
	}

	public Instant getReceivedAt() {
		return receivedAt;
	}

	public ReadingStatus getStatus() {
		return status;
	}

	public BigDecimal getMinTemperature() {
		return minTemperature;
	}

	public BigDecimal getMaxTemperature() {
		return maxTemperature;
	}

}
