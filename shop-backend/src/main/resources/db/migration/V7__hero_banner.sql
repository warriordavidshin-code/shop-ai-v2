-- V7: hero banners for home page image area
CREATE TABLE hero_banner (
    banner_id    BIGSERIAL PRIMARY KEY,
    image_url    VARCHAR(500) NOT NULL,
    overlay_text VARCHAR(500),
    sort_order   INT NOT NULL DEFAULT 0,
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_hero_banner_active_sort ON hero_banner (active, sort_order);
