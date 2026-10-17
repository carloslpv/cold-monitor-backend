-- A sensor outage is a stretch with no readings from an equipment. Alerts go out when one is detected and when the
-- readings resume, instead of on every check.
CREATE TABLE sensor_outage (
    id               UUID           PRIMARY KEY,
    equipment_id     UUID           NOT NULL REFERENCES equipment (id),
    -- arrival time and value of the last reading before the silence
    last_reading_at  TIMESTAMPTZ    NOT NULL,
    last_temperature NUMERIC(5, 2)  NOT NULL,
    -- when the silence was noticed
    detected_at      TIMESTAMPTZ    NOT NULL,
    -- arrival time and value of the first reading after it
    ended_at         TIMESTAMPTZ,
    end_temperature  NUMERIC(5, 2)
);

-- at most one open outage per equipment
CREATE UNIQUE INDEX ux_sensor_outage_open ON sensor_outage (equipment_id) WHERE ended_at IS NULL;

CREATE INDEX ix_sensor_outage_detected ON sensor_outage (detected_at DESC);

-- lets the outage check find each equipment's latest arrival without scanning its readings
CREATE INDEX ix_temperature_reading_equipment_received
    ON temperature_reading (equipment_id, received_at DESC);
