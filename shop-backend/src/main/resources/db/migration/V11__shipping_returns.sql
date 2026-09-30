-- Shipping module: delivery companies, shipments + tracking events, returns, shipping policy.

ALTER TABLE orders DROP CONSTRAINT IF EXISTS ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status CHECK (order_status IN (
    'CREATED', 'PAYMENT_PENDING', 'PAID', 'PREPARING', 'SHIPPED', 'DELIVERED',
    'CANCEL_REQUESTED', 'CANCELLED', 'RETURN_REQUESTED', 'RETURNED'
));

-- Amount actually refunded (a return may deduct the return shipping fee).
ALTER TABLE payment ADD COLUMN refunded_amount NUMERIC(15, 2);

CREATE INDEX IF NOT EXISTS idx_orders_status_ordered ON orders (order_status, ordered_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_ordered_at ON orders (ordered_at DESC);

-- Internal courier codes. External APIs use their own codes (delivery_company_code).
CREATE TABLE delivery_company (
    code                  VARCHAR(30) PRIMARY KEY,
    company_name          VARCHAR(50) NOT NULL,
    tracking_url_template VARCHAR(300),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order            INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE delivery_company_code (
    company_code  VARCHAR(30) NOT NULL REFERENCES delivery_company (code) ON DELETE CASCADE,
    provider      VARCHAR(30) NOT NULL,
    provider_code VARCHAR(30) NOT NULL,
    PRIMARY KEY (company_code, provider)
);

INSERT INTO delivery_company (code, company_name, tracking_url_template, enabled, sort_order) VALUES
    ('CJ', 'CJ대한통운', 'https://trace.cjlogistics.com/next/tracking.html?wblNo={trackingNumber}', TRUE, 10),
    ('HANJIN', '한진택배', 'https://www.hanjin.com/kor/CMS/DeliveryMgr/WaybillResult.do?mCode=MN038&schLang=KR&wblnumText2={trackingNumber}', TRUE, 20),
    ('LOTTE', '롯데택배', 'https://www.lotteglogis.com/home/reservation/tracking/linkView?InvNo={trackingNumber}', TRUE, 30),
    ('POST', '우체국택배', 'https://service.epost.go.kr/trace.RetrieveDomRigiTraceList.comm?sid1={trackingNumber}', TRUE, 40),
    ('LOGEN', '로젠택배', 'https://www.ilogen.com/web/personal/trace/{trackingNumber}', TRUE, 50),
    ('ETC', '기타', NULL, TRUE, 90);

-- SweetTracker (스마트택배) t_code values.
INSERT INTO delivery_company_code (company_code, provider, provider_code) VALUES
    ('CJ', 'SWEETTRACKER', '04'),
    ('HANJIN', 'SWEETTRACKER', '05'),
    ('LOTTE', 'SWEETTRACKER', '08'),
    ('POST', 'SWEETTRACKER', '01'),
    ('LOGEN', 'SWEETTRACKER', '06');

CREATE TABLE return_request (
    return_request_id       BIGSERIAL PRIMARY KEY,
    order_id                BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    member_id               BIGINT NOT NULL REFERENCES member (member_id),
    reason                  VARCHAR(100) NOT NULL,
    memo                    TEXT,
    return_status           VARCHAR(30) NOT NULL,
    pickup_name             VARCHAR(100) NOT NULL,
    pickup_phone            VARCHAR(32) NOT NULL,
    pickup_postcode         VARCHAR(16) NOT NULL,
    pickup_address1         VARCHAR(255) NOT NULL,
    pickup_address2         VARCHAR(255),
    pickup_delivery_company VARCHAR(30) REFERENCES delivery_company (code),
    pickup_tracking_number  VARCHAR(100),
    free_return             BOOLEAN NOT NULL DEFAULT FALSE,
    return_shipping_fee     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    refund_amount           NUMERIC(15, 2),
    restocked               BOOLEAN NOT NULL DEFAULT FALSE,
    reject_reason           VARCHAR(500),
    admin_memo              VARCHAR(500),
    processed_by            BIGINT REFERENCES member (member_id),
    requested_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    approved_at             TIMESTAMPTZ,
    pickup_requested_at     TIMESTAMPTZ,
    picked_up_at            TIMESTAMPTZ,
    received_at             TIMESTAMPTZ,
    completed_at            TIMESTAMPTZ,
    rejected_at             TIMESTAMPTZ,
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                 BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_return_request_status CHECK (return_status IN (
        'REQUESTED', 'APPROVED', 'PICKUP_REQUESTED', 'PICKED_UP', 'IN_TRANSIT', 'RECEIVED', 'REFUNDED', 'REJECTED'
    ))
);

CREATE INDEX idx_return_request_order ON return_request (order_id);
CREATE INDEX idx_return_request_status ON return_request (return_status, requested_at DESC);
-- One return in progress (or completed) per order; a rejected return can be re-requested.
CREATE UNIQUE INDEX uq_return_request_active
    ON return_request (order_id)
    WHERE return_status <> 'REJECTED';

CREATE TABLE shipment (
    shipment_id              BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    return_request_id        BIGINT REFERENCES return_request (return_request_id) ON DELETE CASCADE,
    shipment_type            VARCHAR(16) NOT NULL DEFAULT 'DELIVERY',
    delivery_company         VARCHAR(30) REFERENCES delivery_company (code),
    tracking_number          VARCHAR(100),
    shipment_status          VARCHAR(32) NOT NULL,
    pickup_requested_at      TIMESTAMPTZ,
    picked_up_at             TIMESTAMPTZ,
    shipped_at               TIMESTAMPTZ,
    delivered_at             TIMESTAMPTZ,
    last_tracking_checked_at TIMESTAMPTZ,
    last_tracking_error      VARCHAR(300),
    tracking_fail_count      INT NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipment_type CHECK (shipment_type IN ('DELIVERY', 'RETURN')),
    CONSTRAINT ck_shipment_status CHECK (shipment_status IN (
        'READY', 'PREPARING', 'PICKUP_REQUESTED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED',
        'RETURN_REQUESTED', 'RETURN_PICKUP_REQUESTED', 'RETURN_IN_TRANSIT', 'RETURN_COMPLETED', 'CANCELLED'
    )),
    CONSTRAINT ck_shipment_return_link CHECK (
        (shipment_type = 'DELIVERY' AND return_request_id IS NULL)
        OR (shipment_type = 'RETURN' AND return_request_id IS NOT NULL)
    )
);

CREATE UNIQUE INDEX uq_shipment_delivery_order ON shipment (order_id) WHERE shipment_type = 'DELIVERY';
CREATE UNIQUE INDEX uq_shipment_return_request ON shipment (return_request_id) WHERE return_request_id IS NOT NULL;
CREATE INDEX idx_shipment_order ON shipment (order_id);
CREATE INDEX idx_shipment_tracking_poll ON shipment (shipment_status, last_tracking_checked_at)
    WHERE tracking_number IS NOT NULL;
CREATE INDEX idx_shipment_tracking_number ON shipment (tracking_number);

CREATE TABLE shipment_tracking_event (
    event_id    BIGSERIAL PRIMARY KEY,
    shipment_id BIGINT NOT NULL REFERENCES shipment (shipment_id) ON DELETE CASCADE,
    source      VARCHAR(16) NOT NULL,
    event_time  TIMESTAMPTZ NOT NULL,
    location    VARCHAR(100),
    description VARCHAR(300) NOT NULL,
    status      VARCHAR(32),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipment_tracking_event_source CHECK (source IN ('INTERNAL', 'PROVIDER'))
);

CREATE INDEX idx_shipment_tracking_event_shipment ON shipment_tracking_event (shipment_id, event_time);

-- Single-row shipping fee settings, editable from the admin screen.
CREATE TABLE shipping_policy (
    policy_id             INT PRIMARY KEY DEFAULT 1,
    base_shipping_fee     NUMERIC(15, 2) NOT NULL DEFAULT 3000,
    free_shipping_amount  NUMERIC(15, 2) NOT NULL DEFAULT 50000,
    jeju_extra_fee        NUMERIC(15, 2) NOT NULL DEFAULT 3000,
    remote_area_extra_fee NUMERIC(15, 2) NOT NULL DEFAULT 5000,
    return_shipping_fee   NUMERIC(15, 2) NOT NULL DEFAULT 3000,
    updated_by            BIGINT REFERENCES member (member_id),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_policy_single CHECK (policy_id = 1),
    CONSTRAINT ck_shipping_policy_non_negative CHECK (
        base_shipping_fee >= 0 AND free_shipping_amount >= 0 AND jeju_extra_fee >= 0
        AND remote_area_extra_fee >= 0 AND return_shipping_fee >= 0
    )
);

INSERT INTO shipping_policy (policy_id) VALUES (1);

-- Postcode ranges that pay the Jeju / remote-island surcharge.
CREATE TABLE shipping_extra_area (
    area_id       BIGSERIAL PRIMARY KEY,
    area_type     VARCHAR(16) NOT NULL,
    postcode_from VARCHAR(5) NOT NULL,
    postcode_to   VARCHAR(5) NOT NULL,
    note          VARCHAR(100),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_extra_area_type CHECK (area_type IN ('JEJU', 'REMOTE')),
    CONSTRAINT ck_shipping_extra_area_postcode CHECK (
        postcode_from ~ '^[0-9]{5}$' AND postcode_to ~ '^[0-9]{5}$' AND postcode_from <= postcode_to
    )
);

INSERT INTO shipping_extra_area (area_type, postcode_from, postcode_to, note) VALUES
    ('JEJU', '63000', '63644', '제주특별자치도'),
    ('REMOTE', '40200', '40240', '경북 울릉군'),
    ('REMOTE', '23100', '23116', '인천 옹진군 도서'),
    ('REMOTE', '23124', '23136', '인천 옹진군 도서');
