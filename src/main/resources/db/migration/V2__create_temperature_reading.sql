CREATE TABLE temperature_reading (
    id              UUID           PRIMARY KEY,
    equipment_id    UUID           NOT NULL REFERENCES equipment (id),
    temperature     NUMERIC(5, 2)  NOT NULL,
    recorded_at     TIMESTAMPTZ    NOT NULL,
    received_at     TIMESTAMPTZ    NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    -- thresholds in force when the reading arrived, so later threshold changes do not rewrite history
    min_temperature NUMERIC(5, 2)  NOT NULL,
    max_temperature NUMERIC(5, 2)  NOT NULL
);

CREATE INDEX ix_temperature_reading_equipment_recorded
    ON temperature_reading (equipment_id, recorded_at DESC);

CREATE INDEX ix_temperature_reading_out_of_range
    ON temperature_reading (recorded_at DESC)
    WHERE status <> 'IN_RANGE';
