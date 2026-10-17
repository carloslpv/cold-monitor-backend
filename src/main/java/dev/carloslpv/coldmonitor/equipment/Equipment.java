package dev.carloslpv.coldmonitor.equipment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * A refrigerated asset (fridge, freezer, cold room) whose temperature is monitored.
 * Readings outside {@code [minTemperature, maxTemperature]} are flagged as out of range.
 */
@Entity
@Table(name = "equipment")
public class Equipment {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true, length = 50)
	private String code;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(length = 120)
	private String location;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EquipmentType type;

	@Column(name = "min_temperature", nullable = false, precision = 5, scale = 2)
	private BigDecimal minTemperature;

	@Column(name = "max_temperature", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxTemperature;

	@Column(nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Equipment() {
	}

	public Equipment(String code, String name, String location, EquipmentType type,
			BigDecimal minTemperature, BigDecimal maxTemperature, boolean active) {
		update(code, name, location, type, minTemperature, maxTemperature, active);
	}

	@PrePersist
	void onCreate() {
		this.createdAt = Instant.now();
		this.updatedAt = this.createdAt;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = Instant.now();
	}

	public void update(String code, String name, String location, EquipmentType type,
			BigDecimal minTemperature, BigDecimal maxTemperature, boolean active) {
		this.code = code;
		this.name = name;
		this.location = location;
		this.type = type;
		this.minTemperature = minTemperature;
		this.maxTemperature = maxTemperature;
		this.active = active;
	}

	public UUID getId() {
		return id;
	}

	public String getCode() {
		return code;
	}

	public String getName() {
		return name;
	}

	public String getLocation() {
		return location;
	}

	public EquipmentType getType() {
		return type;
	}

	public BigDecimal getMinTemperature() {
		return minTemperature;
	}

	public BigDecimal getMaxTemperature() {
		return maxTemperature;
	}

	public boolean isActive() {
		return active;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
