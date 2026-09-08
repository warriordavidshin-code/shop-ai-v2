-- V3: cart, order, payment
CREATE TABLE cart (
    cart_id    BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL REFERENCES member (member_id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_member UNIQUE (member_id)
);

CREATE TABLE cart_item (
    cart_item_id BIGSERIAL PRIMARY KEY,
    cart_id      BIGINT NOT NULL REFERENCES cart (cart_id) ON DELETE CASCADE,
    sku_id       BIGINT NOT NULL REFERENCES product_sku (sku_id),
    quantity     INT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_cart_item_sku UNIQUE (cart_id, sku_id),
    CONSTRAINT ck_cart_item_qty CHECK (quantity > 0)
);

CREATE TABLE orders (
    order_id             BIGSERIAL PRIMARY KEY,
    order_no             VARCHAR(40) NOT NULL,
    member_id            BIGINT NOT NULL REFERENCES member (member_id),
    order_status         VARCHAR(32) NOT NULL,
    total_product_amount NUMERIC(15, 2) NOT NULL,
    discount_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    delivery_amount      NUMERIC(15, 2) NOT NULL DEFAULT 0,
    payment_amount       NUMERIC(15, 2) NOT NULL,
    receiver_name        VARCHAR(100) NOT NULL,
    receiver_phone       VARCHAR(32) NOT NULL,
    postcode             VARCHAR(16) NOT NULL,
    address1             VARCHAR(255) NOT NULL,
    address2             VARCHAR(255),
    order_memo           VARCHAR(500),
    idempotency_key      VARCHAR(100) NOT NULL,
    ordered_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_orders_order_no UNIQUE (order_no),
    CONSTRAINT uq_orders_idempotency UNIQUE (idempotency_key),
    CONSTRAINT ck_orders_status CHECK (order_status IN (
        'CREATED', 'PAYMENT_PENDING', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED'
    ))
);

CREATE INDEX idx_orders_member ON orders (member_id);

CREATE TABLE order_item (
    order_item_id  BIGSERIAL PRIMARY KEY,
    order_id       BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    product_id     BIGINT NOT NULL REFERENCES product (product_id),
    sku_id         BIGINT NOT NULL REFERENCES product_sku (sku_id),
    product_name   VARCHAR(200) NOT NULL,
    option_name    VARCHAR(100) NOT NULL,
    quantity       INT NOT NULL,
    unit_price     NUMERIC(15, 2) NOT NULL,
    discount_price NUMERIC(15, 2) NOT NULL DEFAULT 0,
    payment_price  NUMERIC(15, 2) NOT NULL,
    status         VARCHAR(32) NOT NULL DEFAULT 'ORDERED',
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_order_item_qty CHECK (quantity > 0)
);

CREATE TABLE payment (
    payment_id               BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT NOT NULL REFERENCES orders (order_id),
    payment_method           VARCHAR(50) NOT NULL DEFAULT 'MOCK',
    provider                 VARCHAR(50) NOT NULL DEFAULT 'MOCK',
    provider_transaction_id  VARCHAR(100),
    payment_amount           NUMERIC(15, 2) NOT NULL,
    payment_status           VARCHAR(32) NOT NULL,
    approved_at              TIMESTAMPTZ,
    cancelled_at             TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_payment_status CHECK (payment_status IN ('READY', 'APPROVED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_payment_order ON payment (order_id);

CREATE TABLE wishlist (
    wishlist_id BIGSERIAL PRIMARY KEY,
    member_id   BIGINT NOT NULL REFERENCES member (member_id),
    product_id  BIGINT NOT NULL REFERENCES product (product_id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wishlist_member_product UNIQUE (member_id, product_id)
);
