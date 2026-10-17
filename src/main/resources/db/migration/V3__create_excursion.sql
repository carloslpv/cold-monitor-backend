-- An excursion is a continuous stretch of out-of-range readings on one equipment. Alerts go out when one starts and
-- when it ends, instead of once per reading.
CREATE TABLE excursion (
    id                  UUID           PRIMARY KEY,
    equipment_id        UUID           NOT NULL REFERENCES equipment (id),
    -- side of the range the excursion started on (BELOW_MIN or ABOVE_MAX)
    status              VARCHAR(20)    NOT NULL,
    -- thresholds in force when the excursion started
    min_temperature     NUMERIC(5, 2)  NOT NULL,
    max_temperature     NUMERIC(5, 2)  NOT NULL,
    started_at          TIMESTAMPTZ    NOT NULL,
    start_temperature   NUMERIC(5, 2)  NOT NULL,
    lowest_temperature  NUMERIC(5, 2)  NOT NULL,
    highest_temperature NUMERIC(5, 2)  NOT NULL,
    -- first in-range reading of the current recovery streak, and how many in-range readings it has so far
    recovering_since    TIMESTAMPTZ,
    recovery_readings   INTEGER        NOT NULL DEFAULT 0,
    ended_at            TIMESTAMPTZ,
    end_temperature     NUMERIC(5, 2)
);

-- at most one open excursion per equipment
CREATE UNIQUE INDEX ux_excursion_open ON excursion (equipment_id) WHERE ended_at IS NULL;

CREATE INDEX ix_excursion_equipment_started ON excursion (equipment_id, started_at DESC);
