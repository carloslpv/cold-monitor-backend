CREATE TABLE equipment (
    id              UUID           PRIMARY KEY,
    code            VARCHAR(50)    NOT NULL,
    name            VARCHAR(120)   NOT NULL,
    location        VARCHAR(120),
    type            VARCHAR(20)    NOT NULL,
    min_temperature NUMERIC(5, 2)  NOT NULL,
    max_temperature NUMERIC(5, 2)  NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL,
    CONSTRAINT uk_equipment_code UNIQUE (code),
    CONSTRAINT ck_equipment_thresholds CHECK (min_temperature < max_temperature)
);
