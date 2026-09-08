-- V2: catalog & inventory
CREATE TABLE category (
    category_id BIGSERIAL PRIMARY KEY,
    parent_id   BIGINT REFERENCES category (category_id),
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    sort_order  INT NOT NULL DEFAULT 0,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_category_slug UNIQUE (slug)
);

CREATE TABLE product (
    product_id   BIGSERIAL PRIMARY KEY,
    category_id  BIGINT NOT NULL REFERENCES category (category_id),
    product_name VARCHAR(200) NOT NULL,
    brand_name   VARCHAR(100) NOT NULL,
    summary      VARCHAR(500),
    description  TEXT,
    normal_price NUMERIC(15, 2) NOT NULL,
    sale_price   NUMERIC(15, 2) NOT NULL,
    status       VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    fit_type     VARCHAR(50),
    material     VARCHAR(100),
    thickness    VARCHAR(50),
    stretch      VARCHAR(50),
    see_through  VARCHAR(50),
    season       VARCHAR(50),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version      BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'HIDDEN', 'DISCONTINUED')),
    CONSTRAINT ck_product_price CHECK (normal_price >= 0 AND sale_price >= 0)
);

CREATE INDEX idx_product_category ON product (category_id);
CREATE INDEX idx_product_status ON product (status);

CREATE TABLE product_sku (
    sku_id           BIGSERIAL PRIMARY KEY,
    product_id       BIGINT NOT NULL REFERENCES product (product_id),
    sku_code         VARCHAR(64) NOT NULL,
    color            VARCHAR(50) NOT NULL,
    size             VARCHAR(30) NOT NULL,
    additional_price NUMERIC(15, 2) NOT NULL DEFAULT 0,
    status           VARCHAR(32) NOT NULL DEFAULT 'ON_SALE',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_sku_code UNIQUE (sku_code),
    CONSTRAINT uq_product_sku_color_size UNIQUE (product_id, color, size),
    CONSTRAINT ck_sku_status CHECK (status IN ('DRAFT', 'ON_SALE', 'SOLD_OUT', 'HIDDEN', 'DISCONTINUED'))
);

CREATE TABLE inventory (
    inventory_id      BIGSERIAL PRIMARY KEY,
    sku_id            BIGINT NOT NULL REFERENCES product_sku (sku_id),
    stock_quantity    INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    reorder_point     INT NOT NULL DEFAULT 5,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version           BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventory_sku UNIQUE (sku_id),
    CONSTRAINT ck_inventory_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_inventory_reserved CHECK (reserved_quantity >= 0 AND reserved_quantity <= stock_quantity)
);

CREATE TABLE inventory_movement (
    movement_id      BIGSERIAL PRIMARY KEY,
    sku_id           BIGINT NOT NULL REFERENCES product_sku (sku_id),
    movement_type    VARCHAR(32) NOT NULL,
    quantity         INT NOT NULL,
    reference_type   VARCHAR(50),
    reference_id     VARCHAR(100),
    reason           VARCHAR(255),
    actor_member_id  BIGINT REFERENCES member (member_id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_movement_type CHECK (movement_type IN (
        'RECEIPT', 'SALE', 'CANCEL', 'RETURN', 'ADJUSTMENT', 'RESERVATION', 'RELEASE'
    ))
);

CREATE INDEX idx_inventory_movement_sku ON inventory_movement (sku_id);

CREATE TABLE product_image (
    image_id   BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES product (product_id),
    image_url  VARCHAR(500),
    image_type VARCHAR(32) NOT NULL DEFAULT 'MAIN',
    alt_text   VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_image_type CHECK (image_type IN ('MAIN', 'DETAIL', 'MODEL', 'COLOR', 'SIZE'))
);

CREATE TABLE product_measurement (
    measurement_id BIGSERIAL PRIMARY KEY,
    product_id     BIGINT NOT NULL REFERENCES product (product_id),
    size           VARCHAR(30) NOT NULL,
    shoulder       NUMERIC(6, 1),
    chest          NUMERIC(6, 1),
    waist          NUMERIC(6, 1),
    hip            NUMERIC(6, 1),
    sleeve         NUMERIC(6, 1),
    total_length   NUMERIC(6, 1),
    rise           NUMERIC(6, 1),
    thigh          NUMERIC(6, 1),
    hem            NUMERIC(6, 1),
    CONSTRAINT uq_product_measurement_size UNIQUE (product_id, size)
);
