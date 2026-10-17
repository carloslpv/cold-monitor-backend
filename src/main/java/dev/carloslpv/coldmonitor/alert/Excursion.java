package dev.carloslpv.coldmonitor.alert;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import dev.carloslpv.coldmonitor.equipment.Equipment;
import dev.carloslpv.coldmonitor.reading.ReadingStatus;
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
 * A continuous stretch of out-of-range readings on one equipment. It opens on the first out-of-range reading and
 * closes once the temperature has stayed back in range for a configured number of consecutive readings, so a value
 * hovering at the limit does not open and close it on every reading.
 */
@Entity
@Table(name = "excursion")
public class Excursion {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "equipment_id", nullable = false, updatable = false)
	private Equipment equipment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false, length = 20)
	private ReadingStatus status;

	@Column(name = "min_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal minTemperature;

	@Column(name = "max_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal maxTemperature;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	@Column(name = "start_temperature", nullable = false, updatable = false, precision = 5, scale = 2)
	private BigDecimal startTemperature;

	@Column(name = "lowest_temperature", nullable = false, precision = 5, scale = 2)
	private BigDecimal lowestTemperature;

	@Column(name = "highest_temperature", nullable = false, precision = 5, scale = 2)
	private BigDecimal highestTemperature;

	@Column(name = "recovering_since")
	private Instant recoveringSince;

	@Column(name = "recovery_readings", nullable = false)
	private int recoveryReadings;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "end_temperature", precision = 5, scale = 2)
	private BigDecimal endTemperature;

	protected Excursion() {
	}

	Excursion(Equipment equipment, ReadingStatus status, BigDecimal minTemperature, BigDecimal maxTemperature,
			BigDecimal temperature, Instant startedAt) {
		if (!status.isOutOfRange()) {
			throw new IllegalArgumentException("An excursion starts with an out-of-range reading");
		}
		this.equipment = equipment;
		this.status = status;
		this.minTemperature = minTemperature;
		this.maxTemperature = maxTemperature;
		this.startedAt = startedAt;
		this.startTemperature = temperature;
		this.lowestTemperature = temperature;
		this.highestTemperature = temperature;
	}

	/** Another out-of-range reading: the excursion goes on and any recovery streak starts over. */
	void observeOutOfRange(BigDecimal temperature) {
		this.lowestTemperature = this.lowestTemperature.min(temperature);
		this.highestTemperature = this.highestTemperature.max(temperature);
		this.recoveringSince = null;
		this.recoveryReadings = 0;
	}

	/**
	 * An in-range reading. Returns {@code true} when it completes the recovery streak and closes the excursion; the end
	 * time is the first reading of that streak, when the temperature actually came back.
	 */
	boolean observeInRange(BigDecimal temperature, Instant recordedAt, int readingsToRecover) {
		if (this.recoveringSince == null) {
			this.recoveringSince = recordedAt;
		}
		this.recoveryReadings++;
		if (this.recoveryReadings < readingsToRecover) {
			return false;
		}
		this.endedAt = this.recoveringSince;
		this.endTemperature = temperature;
		return true;
	}

	/** The most extreme temperature on the side the excursion started on. */
	public BigDecimal getPeakTemperature() {
		return (this.status == ReadingStatus.ABOVE_MAX) ? this.highestTemperature : this.lowestTemperature;
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

	public ReadingStatus getStatus() {
		return status;
	}

	public BigDecimal getMinTemperature() {
		return minTemperature;
	}

	public BigDecimal getMaxTemperature() {
		return maxTemperature;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public BigDecimal getStartTemperature() {
		return startTemperature;
	}

	public BigDecimal getLowestTemperature() {
		return lowestTemperature;
	}

	public BigDecimal getHighestTemperature() {
		return highestTemperature;
	}

	public Instant getEndedAt() {
		return endedAt;
	}

	public BigDecimal getEndTemperature() {
		return endTemperature;
	}

}
