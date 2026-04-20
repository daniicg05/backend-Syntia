CREATE TABLE bdns_regiones (
    id       INTEGER PRIMARY KEY,
    nombre   VARCHAR(255) NOT NULL,
    nivel    VARCHAR(50),
    activo   BOOLEAN DEFAULT TRUE,
    sync_at  TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_finalidades (
    id      INTEGER PRIMARY KEY,
    nombre  VARCHAR(500) NOT NULL,
    activo  BOOLEAN DEFAULT TRUE,
    sync_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_instrumentos (
    id      INTEGER PRIMARY KEY,
    nombre  VARCHAR(255) NOT NULL,
    activo  BOOLEAN DEFAULT TRUE,
    sync_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE bdns_organos (
    id         INTEGER PRIMARY KEY,
    nombre     VARCHAR(500) NOT NULL,
    tipo_admon CHAR(1) NOT NULL,
    activo     BOOLEAN DEFAULT TRUE,
    sync_at    TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_regiones_nombre
    ON bdns_regiones USING gin(to_tsvector('spanish', nombre));

CREATE INDEX idx_finalidades_nombre
    ON bdns_finalidades USING gin(to_tsvector('spanish', nombre));

CREATE INDEX idx_instrumentos_nombre
    ON bdns_instrumentos USING gin(to_tsvector('spanish', nombre));

CREATE INDEX idx_organos_nombre
    ON bdns_organos USING gin(to_tsvector('spanish', nombre));

