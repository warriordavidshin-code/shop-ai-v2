-- Order cancel requests (member request -> admin approve/reject) and member address book.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (order_status IN (
    'CREATED', 'PAYMENT_PENDING', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCEL_REQUESTED', 'CANCELLED'
));

CREATE TABLE order_cancel_request (
    cancel_request_id     BIGSERIAL PRIMARY KEY,
    order_id              BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    member_id             BIGINT NOT NULL REFERENCES member (member_id),
    status                VARCHAR(32) NOT NULL,
    reason                VARCHAR(500) NOT NULL,
    previous_order_status VARCHAR(32) NOT NULL,
    reject_reason         VARCHAR(500),
    processed_by          BIGINT REFERENCES member (member_id),
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at          TIMESTAMPTZ,
    CONSTRAINT ck_order_cancel_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_order_cancel_request_order ON order_cancel_request (order_id);
CREATE INDEX idx_order_cancel_request_status ON order_cancel_request (status, requested_at);
-- Only one open request per order.
CREATE UNIQUE INDEX uq_order_cancel_request_open
    ON order_cancel_request (order_id)
    WHERE status = 'REQUESTED';

CREATE TABLE member_address (
    address_id     BIGSERIAL PRIMARY KEY,
    member_id      BIGINT NOT NULL REFERENCES member (member_id) ON DELETE CASCADE,
    label          VARCHAR(50) NOT NULL,
    receiver_name  VARCHAR(100) NOT NULL,
    receiver_phone VARCHAR(32) NOT NULL,
    postcode       VARCHAR(16) NOT NULL,
    address1       VARCHAR(255) NOT NULL,
    address2       VARCHAR(255),
    is_default     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_member_address_member ON member_address (member_id);
-- At most one default address per member.
CREATE UNIQUE INDEX uq_member_address_default
    ON member_address (member_id)
    WHERE is_default;
