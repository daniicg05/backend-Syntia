CREATE TABLE IF NOT EXISTS visual_assets (
    id BIGSERIAL PRIMARY KEY,
    asset_key VARCHAR(420) NOT NULL UNIQUE,
    entity_name VARCHAR(300) NOT NULL,
    normalized_entity VARCHAR(300) NOT NULL,
    visual_type VARCHAR(80),
    domain VARCHAR(255),
    image_url TEXT,
    source_url TEXT,
    source_type VARCHAR(80),
    status VARCHAR(40),
    confidence INTEGER,
    usage_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_visual_assets_asset_key
    ON visual_assets (asset_key);

CREATE INDEX IF NOT EXISTS idx_visual_assets_normalized_entity
    ON visual_assets (normalized_entity);

CREATE INDEX IF NOT EXISTS idx_visual_assets_domain
    ON visual_assets (domain);
