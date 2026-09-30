-- Shipping domain refactor.
--   orders ─┬─ shipment (DELIVERY / RETURN / EXCHANGE_*) ── shipment_tracking_event
--           ├─ return_request ── RETURN shipment (shipment.return_request_id)
--           └─ shipping_notification
--   delivery_company (courier) ── delivery_company_provider_code ── shipping_provider (API vendor)
--   shipping_policy, shipping_extra_area, shipping_api_operation
--
-- V11 already ran on existing databases, so this migration rebuilds the shipping tables instead of editing V11.
-- Settings (policy, surcharge areas, couriers, provider codes) are carried over. Shipments / returns / events are
-- copied into shipping_v11_archive (JSONB) before the old tables are dropped; the archive is removed when empty.
-- orders / order_item / payment / member / product data is not touched.

-- ------------------------------------------------------------------ keep V11 data

CREATE TABLE shipping_v11_archive (
    archive_id   BIGSERIAL PRIMARY KEY,
    source_table VARCHAR(60) NOT NULL,
    row_data     JSONB NOT NULL,
    archived_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO shipping_v11_archive (source_table, row_data)
SELECT 'return_request', to_jsonb(t) FROM return_request t;
INSERT INTO shipping_v11_archive (source_table, row_data)
SELECT 'shipment', to_jsonb(t) FROM shipment t;
INSERT INTO shipping_v11_archive (source_table, row_data)
SELECT 'shipment_tracking_event', to_jsonb(t) FROM shipment_tracking_event t;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM shipping_v11_archive) THEN
        DROP TABLE shipping_v11_archive;
    END IF;
END $$;

CREATE TEMP TABLE tmp_v11_delivery_company ON COMMIT DROP AS SELECT * FROM delivery_company;
CREATE TEMP TABLE tmp_v11_delivery_company_code ON COMMIT DROP AS SELECT * FROM delivery_company_code;
CREATE TEMP TABLE tmp_v11_shipping_policy ON COMMIT DROP AS SELECT * FROM shipping_policy;
CREATE TEMP TABLE tmp_v11_shipping_extra_area ON COMMIT DROP AS SELECT * FROM shipping_extra_area;

DROP TABLE shipment_tracking_event;
DROP TABLE shipment;
DROP TABLE return_request;
DROP TABLE delivery_company_code;
DROP TABLE delivery_company;
DROP TABLE shipping_extra_area;
DROP TABLE shipping_policy;

-- ------------------------------------------------------------------ external API vendors

-- Secrets (API key, client secret) stay in environment variables; this table only holds on/off switches.
-- A capability is used only when the flag is on AND the Java client implements it AND it is configured.
CREATE TABLE shipping_provider (
    shipping_provider_id  BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(30) NOT NULL,
    name                  VARCHAR(50) NOT NULL,
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    tracking_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    waybill_enabled       BOOLEAN NOT NULL DEFAULT FALSE,
    pickup_enabled        BOOLEAN NOT NULL DEFAULT FALSE,
    return_pickup_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order            INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_shipping_provider_code UNIQUE (code),
    CONSTRAINT ck_shipping_provider_code CHECK (code ~ '^[A-Z0-9_]{2,30}$')
);

INSERT INTO shipping_provider (code, name, enabled, tracking_enabled, waybill_enabled, pickup_enabled,
                               return_pickup_enabled, sort_order) VALUES
    ('SWEETTRACKER', '스마트택배(SweetTracker)', TRUE, TRUE, FALSE, FALSE, FALSE, 10),
    ('GOODSFLOW', '굿스플로(Goodsflow)', FALSE, TRUE, TRUE, TRUE, TRUE, 20),
    ('MANUAL', '수동 처리', TRUE, FALSE, FALSE, TRUE, TRUE, 90);

-- ------------------------------------------------------------------ couriers

CREATE TABLE delivery_company (
    delivery_company_id   BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(30) NOT NULL,
    name                  VARCHAR(50) NOT NULL,
    tracking_url_template VARCHAR(300),
    enabled               BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order            INT NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delivery_company_code UNIQUE (code)
);

INSERT INTO delivery_company (code, name, tracking_url_template, enabled, sort_order, created_at)
SELECT code, company_name, tracking_url_template, enabled, sort_order, created_at
FROM tmp_v11_delivery_company
ORDER BY sort_order, code;

-- Courier code in each vendor's own code system (e.g. HANJIN -> SWEETTRACKER '05').
CREATE TABLE delivery_company_provider_code (
    delivery_company_provider_code_id BIGSERIAL PRIMARY KEY,
    delivery_company_id   BIGINT NOT NULL REFERENCES delivery_company (delivery_company_id) ON DELETE CASCADE,
    shipping_provider_id  BIGINT NOT NULL REFERENCES shipping_provider (shipping_provider_id) ON DELETE CASCADE,
    external_company_code VARCHAR(30) NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_delivery_company_provider_code UNIQUE (delivery_company_id, shipping_provider_id)
);

CREATE INDEX idx_delivery_company_provider_code_provider ON delivery_company_provider_code (shipping_provider_id);

INSERT INTO delivery_company_provider_code (delivery_company_id, shipping_provider_id, external_company_code)
SELECT d.delivery_company_id, p.shipping_provider_id, t.provider_code
FROM tmp_v11_delivery_company_code t
JOIN delivery_company d ON d.code = t.company_code
JOIN shipping_provider p ON p.code = t.provider;

-- ------------------------------------------------------------------ fees (whole won, BIGINT)

-- Several policies may exist; exactly one can be enabled at a time.
CREATE TABLE shipping_policy (
    shipping_policy_id      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(50) NOT NULL,
    base_shipping_fee       BIGINT NOT NULL,
    free_shipping_threshold BIGINT NOT NULL,
    jeju_extra_fee          BIGINT NOT NULL,
    remote_area_extra_fee   BIGINT NOT NULL,
    return_shipping_fee     BIGINT NOT NULL,
    exchange_shipping_fee   BIGINT NOT NULL,
    enabled                 BOOLEAN NOT NULL DEFAULT TRUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_policy_non_negative CHECK (
        base_shipping_fee >= 0 AND free_shipping_threshold >= 0 AND jeju_extra_fee >= 0
        AND remote_area_extra_fee >= 0 AND return_shipping_fee >= 0 AND exchange_shipping_fee >= 0
    )
);

CREATE UNIQUE INDEX uq_shipping_policy_enabled ON shipping_policy (enabled) WHERE enabled;

INSERT INTO shipping_policy (name, base_shipping_fee, free_shipping_threshold, jeju_extra_fee, remote_area_extra_fee,
                             return_shipping_fee, exchange_shipping_fee, enabled)
SELECT '기본 배송정책', ROUND(base_shipping_fee)::BIGINT, ROUND(free_shipping_amount)::BIGINT, ROUND(jeju_extra_fee)::BIGINT,
       ROUND(remote_area_extra_fee)::BIGINT, ROUND(return_shipping_fee)::BIGINT, ROUND(return_shipping_fee * 2)::BIGINT, TRUE
FROM tmp_v11_shipping_policy
ORDER BY policy_id
LIMIT 1;

INSERT INTO shipping_policy (name, base_shipping_fee, free_shipping_threshold, jeju_extra_fee, remote_area_extra_fee,
                             return_shipping_fee, exchange_shipping_fee, enabled)
SELECT '기본 배송정책', 3000, 50000, 3000, 5000, 3000, 6000, TRUE
WHERE NOT EXISTS (SELECT 1 FROM shipping_policy);

-- Postcode ranges with a surcharge. extra_fee NULL = the policy's fee for area_type.
-- source marks where a row came from (MANUAL entry or an imported official list) so imports can be refreshed.
CREATE TABLE shipping_extra_area (
    shipping_extra_area_id BIGSERIAL PRIMARY KEY,
    area_type              VARCHAR(16) NOT NULL,
    area_name              VARCHAR(100) NOT NULL,
    postal_code_from       VARCHAR(5) NOT NULL,
    postal_code_to         VARCHAR(5) NOT NULL,
    extra_fee              BIGINT,
    enabled                BOOLEAN NOT NULL DEFAULT TRUE,
    source                 VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_extra_area_type CHECK (area_type IN ('JEJU', 'REMOTE')),
    CONSTRAINT ck_shipping_extra_area_source CHECK (source IN ('MANUAL', 'OFFICIAL')),
    CONSTRAINT ck_shipping_extra_area_fee CHECK (extra_fee IS NULL OR extra_fee >= 0),
    CONSTRAINT ck_shipping_extra_area_postal_code CHECK (
        postal_code_from ~ '^[0-9]{5}$' AND postal_code_to ~ '^[0-9]{5}$' AND postal_code_from <= postal_code_to
    )
);

CREATE INDEX idx_shipping_extra_area_range ON shipping_extra_area (postal_code_from, postal_code_to) WHERE enabled;

INSERT INTO shipping_extra_area (area_type, area_name, postal_code_from, postal_code_to, created_at)
SELECT area_type, COALESCE(NULLIF(TRIM(note), ''), area_type), postcode_from, postcode_to, created_at
FROM tmp_v11_shipping_extra_area
ORDER BY area_id;

-- ------------------------------------------------------------------ returns (business state only)

-- The pickup address, courier and invoice of a return live on its RETURN shipment.
CREATE TABLE return_request (
    return_request_id   BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    reason_code         VARCHAR(30) NOT NULL,
    reason_text         VARCHAR(1000),
    customer_memo       VARCHAR(500),
    status              VARCHAR(30) NOT NULL,
    free_return         BOOLEAN NOT NULL DEFAULT FALSE,
    return_shipping_fee BIGINT NOT NULL DEFAULT 0,
    refund_amount       BIGINT,
    restocked           BOOLEAN NOT NULL DEFAULT FALSE,
    reject_reason       VARCHAR(500),
    admin_memo          VARCHAR(500),
    requested_at        TIMESTAMPTZ NOT NULL,
    approved_at         TIMESTAMPTZ,
    received_at         TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    rejected_at         TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_return_request_status CHECK (status IN (
        'REQUESTED', 'APPROVED', 'PICKUP_REQUESTED', 'IN_PROGRESS', 'RECEIVED', 'COMPLETED', 'REJECTED', 'CANCELLED'
    )),
    CONSTRAINT ck_return_request_amounts CHECK (
        return_shipping_fee >= 0 AND (refund_amount IS NULL OR refund_amount >= 0)
    )
);

CREATE INDEX idx_return_request_order ON return_request (order_id);
CREATE INDEX idx_return_request_status ON return_request (status, requested_at DESC);
-- One open (or completed) return per order; rejected / cancelled returns can be requested again.
CREATE UNIQUE INDEX uq_return_request_active ON return_request (order_id) WHERE status NOT IN ('REJECTED', 'CANCELLED');

-- ------------------------------------------------------------------ physical movements

CREATE TABLE shipment (
    shipment_id              BIGSERIAL PRIMARY KEY,
    order_id                 BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    shipment_type            VARCHAR(20) NOT NULL,
    return_request_id        BIGINT REFERENCES return_request (return_request_id) ON DELETE CASCADE,
    delivery_company_id      BIGINT REFERENCES delivery_company (delivery_company_id),
    shipping_provider_id     BIGINT REFERENCES shipping_provider (shipping_provider_id),
    tracking_number          VARCHAR(50),
    status                   VARCHAR(32) NOT NULL,
    -- Customer-side address (delivery destination / return pickup origin). NULL for DELIVERY: the order's
    -- receiver snapshot is used, which never changes after checkout.
    contact_name             VARCHAR(100),
    contact_phone            VARCHAR(32),
    postal_code              VARCHAR(16),
    address1                 VARCHAR(255),
    address2                 VARCHAR(255),
    pickup_requested_at      TIMESTAMPTZ,
    picked_up_at             TIMESTAMPTZ,
    shipped_at               TIMESTAMPTZ,
    out_for_delivery_at      TIMESTAMPTZ,
    delivered_at             TIMESTAMPTZ,
    last_tracking_checked_at TIMESTAMPTZ,
    last_tracking_error      VARCHAR(300),
    tracking_fail_count      INT NOT NULL DEFAULT 0,
    last_status_changed_at   TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipment_type CHECK (shipment_type IN ('DELIVERY', 'RETURN', 'EXCHANGE_RETURN', 'EXCHANGE_DELIVERY')),
    CONSTRAINT ck_shipment_status CHECK (status IN (
        'READY', 'WAYBILL_ISSUED', 'PICKUP_REQUESTED', 'PICKED_UP', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED',
        'CANCELLED', 'FAILED'
    )),
    CONSTRAINT ck_shipment_return_link CHECK ((shipment_type = 'RETURN') = (return_request_id IS NOT NULL)),
    CONSTRAINT ck_shipment_tracking_company CHECK (tracking_number IS NULL OR delivery_company_id IS NOT NULL),
    CONSTRAINT ck_shipment_customer_address CHECK (
        shipment_type = 'DELIVERY'
        OR (contact_name IS NOT NULL AND contact_phone IS NOT NULL AND postal_code IS NOT NULL AND address1 IS NOT NULL)
    )
);

-- One delivery per order (drop this index if split shipments are introduced).
CREATE UNIQUE INDEX uq_shipment_delivery_order ON shipment (order_id) WHERE shipment_type = 'DELIVERY';
CREATE UNIQUE INDEX uq_shipment_return_request ON shipment (return_request_id) WHERE return_request_id IS NOT NULL;
-- Invoice numbers are unique per courier; shipments without an invoice yet (NULL) are not constrained.
CREATE UNIQUE INDEX uq_shipment_company_tracking ON shipment (delivery_company_id, tracking_number)
    WHERE tracking_number IS NOT NULL AND status <> 'CANCELLED';
CREATE INDEX idx_shipment_order ON shipment (order_id, shipment_type);
CREATE INDEX idx_shipment_tracking_number ON shipment (tracking_number) WHERE tracking_number IS NOT NULL;
CREATE INDEX idx_shipment_type_status ON shipment (shipment_type, status);
-- Tracking scheduler: WHERE status IN (...) ORDER BY last_tracking_checked_at NULLS FIRST.
CREATE INDEX idx_shipment_tracking_poll ON shipment (status, last_tracking_checked_at NULLS FIRST)
    WHERE tracking_number IS NOT NULL;

-- Append-only history. PROVIDER events are de-duplicated by raw_hash / external_event_id; INTERNAL events are
-- shop actions (admin changes carry actor_member_id).
CREATE TABLE shipment_tracking_event (
    tracking_event_id BIGSERIAL PRIMARY KEY,
    shipment_id       BIGINT NOT NULL REFERENCES shipment (shipment_id) ON DELETE CASCADE,
    source            VARCHAR(16) NOT NULL,
    external_event_id VARCHAR(100),
    status            VARCHAR(32),
    provider_status   VARCHAR(100),
    location          VARCHAR(100),
    description       VARCHAR(300) NOT NULL,
    event_at          TIMESTAMPTZ NOT NULL,
    raw_hash          VARCHAR(64),
    actor_member_id   BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipment_tracking_event_source CHECK (source IN ('INTERNAL', 'PROVIDER')),
    CONSTRAINT ck_shipment_tracking_event_hash CHECK (source = 'INTERNAL' OR raw_hash IS NOT NULL)
);

CREATE INDEX idx_shipment_tracking_event_shipment ON shipment_tracking_event (shipment_id, event_at);
CREATE UNIQUE INDEX uq_shipment_tracking_event_hash ON shipment_tracking_event (shipment_id, raw_hash)
    WHERE raw_hash IS NOT NULL;
CREATE UNIQUE INDEX uq_shipment_tracking_event_external ON shipment_tracking_event (shipment_id, external_event_id)
    WHERE external_event_id IS NOT NULL;

-- ------------------------------------------------------------------ external API operations

-- One row per state-changing vendor call (waybill, pickup, return pickup) keyed by idempotency_key, plus failed
-- tracking calls and connection tests. The row is committed as SUCCEEDED right after the vendor answers and
-- applied_at is set in the same transaction that updates the shipment, so "vendor succeeded but our save failed"
-- is recoverable without calling the vendor twice. No raw request/response bodies: response_summary is sanitized.
CREATE TABLE shipping_api_operation (
    shipping_api_operation_id BIGSERIAL PRIMARY KEY,
    provider_code      VARCHAR(30) NOT NULL,
    operation_type     VARCHAR(30) NOT NULL,
    order_id           BIGINT REFERENCES orders (order_id) ON DELETE SET NULL,
    shipment_id        BIGINT REFERENCES shipment (shipment_id) ON DELETE SET NULL,
    return_request_id  BIGINT REFERENCES return_request (return_request_id) ON DELETE SET NULL,
    idempotency_key    VARCHAR(150),
    request_id         VARCHAR(64) NOT NULL,
    external_reference VARCHAR(100),
    status             VARCHAR(20) NOT NULL,
    http_status        INT,
    error_code         VARCHAR(50),
    error_message      VARCHAR(300),
    response_summary   VARCHAR(1000),
    attempt_count      INT NOT NULL DEFAULT 1,
    requested_at       TIMESTAMPTZ NOT NULL,
    completed_at       TIMESTAMPTZ,
    applied_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version            BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_shipping_api_operation_type CHECK (operation_type IN (
        'TRACKING', 'WAYBILL_ISSUE', 'WAYBILL_CANCEL', 'PICKUP_REQUEST', 'RETURN_PICKUP', 'CONNECTION_TEST'
    )),
    CONSTRAINT ck_shipping_api_operation_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED', 'UNKNOWN'))
);

CREATE UNIQUE INDEX uq_shipping_api_operation_idempotency ON shipping_api_operation (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_shipping_api_operation_status ON shipping_api_operation (status, requested_at DESC);
CREATE INDEX idx_shipping_api_operation_shipment ON shipping_api_operation (shipment_id);
CREATE INDEX idx_shipping_api_operation_order ON shipping_api_operation (order_id);
CREATE INDEX idx_shipping_api_operation_return ON shipping_api_operation (return_request_id);

-- ------------------------------------------------------------------ customer notifications

-- One row per (shipment, event) so a notification is sent at most once even if tracking replays a status.
CREATE TABLE shipping_notification (
    shipping_notification_id BIGSERIAL PRIMARY KEY,
    order_id      BIGINT NOT NULL REFERENCES orders (order_id) ON DELETE CASCADE,
    shipment_id   BIGINT REFERENCES shipment (shipment_id) ON DELETE CASCADE,
    member_id     BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    event_type    VARCHAR(40) NOT NULL,
    channel       VARCHAR(20) NOT NULL,
    status        VARCHAR(20) NOT NULL,
    message       VARCHAR(300) NOT NULL,
    error_message VARCHAR(300),
    sent_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_shipping_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE UNIQUE INDEX uq_shipping_notification_event ON shipping_notification (shipment_id, event_type)
    WHERE shipment_id IS NOT NULL;
CREATE INDEX idx_shipping_notification_order ON shipping_notification (order_id);
CREATE INDEX idx_shipping_notification_pending ON shipping_notification (status, created_at)
    WHERE status IN ('PENDING', 'FAILED');
