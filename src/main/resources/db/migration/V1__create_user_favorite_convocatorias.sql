CREATE TABLE IF NOT EXISTS user_favorite_convocatorias (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    convocatoria_id BIGINT NOT NULL,
    titulo VARCHAR(500) NOT NULL,
    organismo VARCHAR(500),
    ubicacion VARCHAR(500),
    tipo VARCHAR(120),
    sector VARCHAR(120),
    fecha_publicacion DATE,
    fecha_cierre DATE,
    presupuesto NUMERIC(18,2),
    abierto BOOLEAN,
    url_oficial TEXT,
    id_bdns VARCHAR(120),
    numero_convocatoria VARCHAR(120),
    estado_solicitud VARCHAR(30) NOT NULL DEFAULT 'no_solicitada',
    guardada_en TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_fav_usuario FOREIGN KEY (user_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT uk_fav_user_convocatoria UNIQUE (user_id, convocatoria_id),
    CONSTRAINT chk_fav_estado CHECK (estado_solicitud IN ('no_solicitada', 'solicitada'))
);

CREATE INDEX IF NOT EXISTS idx_fav_user_guardada_en
    ON user_favorite_convocatorias (user_id, guardada_en DESC);

CREATE INDEX IF NOT EXISTS idx_fav_user_estado
    ON user_favorite_convocatorias (user_id, estado_solicitud);

CREATE INDEX IF NOT EXISTS idx_fav_user_convocatoria
    ON user_favorite_convocatorias (user_id, convocatoria_id);

