-- Purchase → receiving → inventory → cost → pricing → profit integration.
-- Additive only: existing rows keep their values; new columns are nullable or have defaults.
-- Manual rollback script: docs/db-rollback/V14__rollback.sql

-- ------------------------------------------------------------------ suppliers

CREATE TABLE supplier (
    supplier_id   BIGSERIAL PRIMARY KEY,
    supplier_name VARCHAR(200) NOT NULL,
    business_no   VARCHAR(20),
    contact_name  VARCHAR(100),
    phone         VARCHAR(40),
    address       VARCHAR(300),
    lead_days     INT,
    memo          VARCHAR(1000),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_supplier_lead_days CHECK (lead_days IS NULL OR lead_days >= 0)
);

CREATE UNIQUE INDEX uq_supplier_name ON supplier (LOWER(supplier_name));
CREATE INDEX idx_supplier_business_no ON supplier (business_no) WHERE business_no IS NOT NULL;

-- Supplier product code → internal SKU. The same supplier code can cover several colours / sizes.
CREATE TABLE supplier_product_mapping (
    supplier_product_mapping_id BIGSERIAL PRIMARY KEY,
    supplier_id                 BIGINT NOT NULL REFERENCES supplier (supplier_id) ON DELETE CASCADE,
    supplier_product_code       VARCHAR(100),
    supplier_product_name       VARCHAR(200),
    product_id                  BIGINT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    sku_id                      BIGINT REFERENCES product_sku (sku_id) ON DELETE CASCADE,
    color                       VARCHAR(50),
    size                        VARCHAR(30),
    last_unit_cost              BIGINT,
    last_purchase_date          DATE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_supplier_mapping_code_sku ON supplier_product_mapping (
    supplier_id, LOWER(COALESCE(supplier_product_code, '')), LOWER(COALESCE(supplier_product_name, '')),
    COALESCE(sku_id, 0)
);
CREATE INDEX idx_supplier_mapping_code ON supplier_product_mapping (supplier_id, LOWER(supplier_product_code));
CREATE INDEX idx_supplier_mapping_product ON supplier_product_mapping (product_id);

-- ------------------------------------------------------------------ ledger extensions

ALTER TABLE purchase_ledger ADD COLUMN supplier_id BIGINT REFERENCES supplier (supplier_id) ON DELETE SET NULL;
ALTER TABLE purchase_ledger ADD COLUMN supply_amount BIGINT;
ALTER TABLE purchase_ledger ADD COLUMN tax_amount BIGINT;
ALTER TABLE purchase_ledger ADD CONSTRAINT ck_purchase_ledger_supply_tax CHECK (
    (supply_amount IS NULL OR supply_amount >= 0) AND (tax_amount IS NULL OR tax_amount >= 0)
);
CREATE INDEX idx_purchase_ledger_supplier_id ON purchase_ledger (supplier_id) WHERE deleted_at IS NULL;

ALTER TABLE purchase_ledger_item ADD COLUMN color VARCHAR(50);
ALTER TABLE purchase_ledger_item ADD COLUMN size VARCHAR(30);
ALTER TABLE purchase_ledger_item ADD COLUMN supplier_product_code VARCHAR(100);
ALTER TABLE purchase_ledger_item ADD COLUMN supply_amount BIGINT;
ALTER TABLE purchase_ledger_item ADD COLUMN tax_amount BIGINT;
ALTER TABLE purchase_ledger_item ADD COLUMN match_status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED';
ALTER TABLE purchase_ledger_item ADD COLUMN matched_product_id BIGINT REFERENCES product (product_id) ON DELETE SET NULL;
ALTER TABLE purchase_ledger_item ADD COLUMN matched_sku_id BIGINT REFERENCES product_sku (sku_id) ON DELETE SET NULL;
ALTER TABLE purchase_ledger_item ADD COLUMN match_score NUMERIC(5, 4);
ALTER TABLE purchase_ledger_item ADD COLUMN match_note VARCHAR(500);
ALTER TABLE purchase_ledger_item ADD COLUMN received_quantity INT NOT NULL DEFAULT 0;
ALTER TABLE purchase_ledger_item ADD CONSTRAINT ck_purchase_ledger_item_match CHECK (
    match_status IN ('UNMATCHED', 'MATCHED', 'CANDIDATE', 'NEW_PRODUCT', 'IGNORED')
);
ALTER TABLE purchase_ledger_item ADD CONSTRAINT ck_purchase_ledger_item_received CHECK (received_quantity >= 0);
CREATE INDEX idx_purchase_ledger_item_match ON purchase_ledger_item (match_status);
CREATE INDEX idx_purchase_ledger_item_sku ON purchase_ledger_item (matched_sku_id);

-- ------------------------------------------------------------------ receiving (입고)

-- A receipt is created from a ledger entry (PENDING) and confirmed once with the counted quantities.
-- Confirmation flips status with a conditional update, so a repeated confirm never adds stock twice.
CREATE TABLE purchase_receipt (
    purchase_receipt_id BIGSERIAL PRIMARY KEY,
    receipt_no          VARCHAR(30) NOT NULL,
    purchase_ledger_id  BIGINT REFERENCES purchase_ledger (purchase_ledger_id) ON DELETE SET NULL,
    supplier_id         BIGINT REFERENCES supplier (supplier_id) ON DELETE SET NULL,
    receipt_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expected_date       DATE,
    freight_cost        BIGINT NOT NULL DEFAULT 0,
    incidental_cost     BIGINT NOT NULL DEFAULT 0,
    packaging_cost      BIGINT NOT NULL DEFAULT 0,
    other_cost          BIGINT NOT NULL DEFAULT 0,
    allocation_method   VARCHAR(20) NOT NULL DEFAULT 'AMOUNT',
    memo                VARCHAR(1000),
    confirmed_at        TIMESTAMPTZ,
    confirmed_by        BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    cancelled_at        TIMESTAMPTZ,
    cancelled_by        BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_by          BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_receipt_no UNIQUE (receipt_no),
    CONSTRAINT ck_purchase_receipt_status CHECK (receipt_status IN ('PENDING', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_purchase_receipt_allocation CHECK (allocation_method IN ('QUANTITY', 'AMOUNT')),
    CONSTRAINT ck_purchase_receipt_costs CHECK (
        freight_cost >= 0 AND incidental_cost >= 0 AND packaging_cost >= 0 AND other_cost >= 0
    )
);

CREATE INDEX idx_purchase_receipt_ledger ON purchase_receipt (purchase_ledger_id);
CREATE INDEX idx_purchase_receipt_status ON purchase_receipt (receipt_status, created_at DESC);

CREATE TABLE purchase_receipt_item (
    purchase_receipt_item_id BIGSERIAL PRIMARY KEY,
    purchase_receipt_id      BIGINT NOT NULL REFERENCES purchase_receipt (purchase_receipt_id) ON DELETE CASCADE,
    purchase_ledger_item_id  BIGINT REFERENCES purchase_ledger_item (purchase_ledger_item_id) ON DELETE SET NULL,
    product_id               BIGINT NOT NULL REFERENCES product (product_id),
    sku_id                   BIGINT NOT NULL REFERENCES product_sku (sku_id),
    expected_quantity        INT NOT NULL,
    received_quantity        INT,
    unit_cost                BIGINT NOT NULL,
    allocated_cost           NUMERIC(15, 2),
    landed_unit_cost         NUMERIC(15, 4),
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_purchase_receipt_item_qty CHECK (
        expected_quantity >= 0 AND (received_quantity IS NULL OR received_quantity >= 0)
    ),
    CONSTRAINT ck_purchase_receipt_item_cost CHECK (unit_cost >= 0)
);

CREATE INDEX idx_purchase_receipt_item_receipt ON purchase_receipt_item (purchase_receipt_id);
CREATE INDEX idx_purchase_receipt_item_ledger_item ON purchase_receipt_item (purchase_ledger_item_id);
CREATE INDEX idx_purchase_receipt_item_sku ON purchase_receipt_item (sku_id);

-- ------------------------------------------------------------------ inventory movement extensions

ALTER TABLE inventory_movement ADD COLUMN product_id BIGINT REFERENCES product (product_id) ON DELETE SET NULL;
ALTER TABLE inventory_movement ADD COLUMN stock_before INT;
ALTER TABLE inventory_movement ADD COLUMN stock_after INT;
ALTER TABLE inventory_movement ADD COLUMN purchase_receipt_id BIGINT
    REFERENCES purchase_receipt (purchase_receipt_id) ON DELETE SET NULL;
ALTER TABLE inventory_movement ADD COLUMN unit_cost NUMERIC(15, 4);

ALTER TABLE inventory_movement DROP CONSTRAINT ck_movement_type;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_movement_type CHECK (movement_type IN (
    'RECEIPT', 'SALE', 'CANCEL', 'RETURN', 'ADJUSTMENT', 'RESERVATION', 'RELEASE', 'DAMAGE'
));

-- Backstop against double receiving: one RECEIPT movement per receipt line.
CREATE UNIQUE INDEX uq_inventory_movement_receipt_line ON inventory_movement (reference_type, reference_id)
    WHERE movement_type = 'RECEIPT' AND reference_type = 'PURCHASE_RECEIPT_ITEM';
CREATE INDEX idx_inventory_movement_created ON inventory_movement (created_at DESC);
CREATE INDEX idx_inventory_movement_type_created ON inventory_movement (movement_type, created_at DESC);

-- ------------------------------------------------------------------ cost

-- Moving weighted-average cost per SKU (inventory valuation / COGS basis when costing method is WEIGHTED_AVERAGE).
CREATE TABLE sku_cost (
    sku_id           BIGINT PRIMARY KEY REFERENCES product_sku (sku_id) ON DELETE CASCADE,
    avg_unit_cost    NUMERIC(15, 4) NOT NULL,
    costed_quantity  INT NOT NULL DEFAULT 0,
    last_unit_cost   NUMERIC(15, 4),
    last_received_at TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version          BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_sku_cost_values CHECK (avg_unit_cost >= 0 AND costed_quantity >= 0)
);

-- One row per confirmed receipt line; never updated, so purchase cost history is preserved.
CREATE TABLE product_cost_history (
    product_cost_history_id  BIGSERIAL PRIMARY KEY,
    product_id               BIGINT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    sku_id                   BIGINT REFERENCES product_sku (sku_id) ON DELETE SET NULL,
    purchase_receipt_item_id BIGINT REFERENCES purchase_receipt_item (purchase_receipt_item_id) ON DELETE SET NULL,
    purchase_ledger_id       BIGINT REFERENCES purchase_ledger (purchase_ledger_id) ON DELETE SET NULL,
    supplier_id              BIGINT REFERENCES supplier (supplier_id) ON DELETE SET NULL,
    quantity                 INT NOT NULL,
    unit_cost                NUMERIC(15, 4) NOT NULL,
    allocated_unit_cost      NUMERIC(15, 4) NOT NULL DEFAULT 0,
    landed_unit_cost         NUMERIC(15, 4) NOT NULL,
    total_cost               NUMERIC(15, 2) NOT NULL,
    avg_cost_before          NUMERIC(15, 4),
    avg_cost_after           NUMERIC(15, 4),
    received_at              TIMESTAMPTZ NOT NULL,
    created_by               BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_product_cost_history_receipt_item UNIQUE (purchase_receipt_item_id),
    CONSTRAINT ck_product_cost_history_values CHECK (quantity > 0 AND unit_cost >= 0 AND landed_unit_cost >= 0)
);

CREATE INDEX idx_product_cost_history_product ON product_cost_history (product_id, received_at DESC);
CREATE INDEX idx_product_cost_history_sku ON product_cost_history (sku_id, received_at DESC);

-- ------------------------------------------------------------------ pricing

-- Single-row settings (id = 1). Rates are fractions (0.5 = 50%).
CREATE TABLE product_pricing_policy (
    pricing_policy_id        BIGINT PRIMARY KEY,
    competitive_margin_rate  NUMERIC(5, 4) NOT NULL DEFAULT 0.40,
    standard_margin_rate     NUMERIC(5, 4) NOT NULL DEFAULT 0.50,
    premium_margin_rate      NUMERIC(5, 4) NOT NULL DEFAULT 0.55,
    minimum_margin_rate      NUMERIC(5, 4) NOT NULL DEFAULT 0.40,
    rounding_mode            VARCHAR(30) NOT NULL DEFAULT 'END_900_ROUND_9000',
    margin_basis             VARCHAR(20) NOT NULL DEFAULT 'VAT_INCLUDED',
    tax_type                 VARCHAR(20) NOT NULL DEFAULT 'GENERAL',
    vat_rate                 NUMERIC(5, 4) NOT NULL DEFAULT 0.10,
    costing_method           VARCHAR(30) NOT NULL DEFAULT 'WEIGHTED_AVERAGE',
    payment_fee_rate         NUMERIC(5, 4) NOT NULL DEFAULT 0.0330,
    shipping_cost_per_order  BIGINT NOT NULL DEFAULT 3000,
    other_selling_cost_rate  NUMERIC(5, 4) NOT NULL DEFAULT 0,
    low_stock_cover_days     INT NOT NULL DEFAULT 14,
    safety_stock_days        INT NOT NULL DEFAULT 7,
    default_lead_days        INT NOT NULL DEFAULT 3,
    sales_window_days        INT NOT NULL DEFAULT 30,
    slow_moving_days         INT NOT NULL DEFAULT 30,
    slow_moving_max_sales    INT NOT NULL DEFAULT 2,
    updated_by               BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version                  BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_pricing_policy_singleton CHECK (pricing_policy_id = 1),
    CONSTRAINT ck_pricing_policy_rates CHECK (
        competitive_margin_rate >= 0 AND competitive_margin_rate < 1
        AND standard_margin_rate >= 0 AND standard_margin_rate < 1
        AND premium_margin_rate >= 0 AND premium_margin_rate < 1
        AND minimum_margin_rate >= 0 AND minimum_margin_rate < 1
        AND vat_rate >= 0 AND vat_rate < 1
        AND payment_fee_rate >= 0 AND payment_fee_rate < 1
        AND other_selling_cost_rate >= 0 AND other_selling_cost_rate < 1
    ),
    CONSTRAINT ck_pricing_policy_rounding CHECK (
        rounding_mode IN ('NONE', 'UP_100', 'UP_1000', 'END_900', 'END_900_ROUND_9000')
    ),
    CONSTRAINT ck_pricing_policy_basis CHECK (margin_basis IN ('VAT_INCLUDED', 'VAT_EXCLUDED')),
    CONSTRAINT ck_pricing_policy_tax CHECK (tax_type IN ('GENERAL', 'SIMPLIFIED', 'EXEMPT')),
    CONSTRAINT ck_pricing_policy_costing CHECK (costing_method IN ('RECENT', 'WEIGHTED_AVERAGE', 'STANDARD')),
    CONSTRAINT ck_pricing_policy_days CHECK (
        low_stock_cover_days >= 0 AND safety_stock_days >= 0 AND default_lead_days >= 0
        AND sales_window_days > 0 AND slow_moving_days > 0 AND slow_moving_max_sales >= 0
        AND shipping_cost_per_order >= 0
    )
);

INSERT INTO product_pricing_policy (pricing_policy_id) VALUES (1);

CREATE TABLE product_price_history (
    product_price_history_id BIGSERIAL PRIMARY KEY,
    product_id               BIGINT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    normal_price_before      NUMERIC(15, 2),
    normal_price_after       NUMERIC(15, 2) NOT NULL,
    sale_price_before        NUMERIC(15, 2),
    sale_price_after         NUMERIC(15, 2) NOT NULL,
    reason                   VARCHAR(200),
    changed_by               BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    changed_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_product_price_history_product ON product_price_history (product_id, changed_at DESC);

-- ------------------------------------------------------------------ order cost snapshot

-- Unit cost captured when the sale is confirmed (payment approval), so later cost changes
-- do not rewrite past profit. Orders created before this migration keep NULL (reported as estimated).
ALTER TABLE order_item ADD COLUMN unit_cost NUMERIC(15, 4);
ALTER TABLE order_item ADD COLUMN cost_method VARCHAR(30);

-- ------------------------------------------------------------------ AI recommendations

CREATE TABLE ai_recommendation_history (
    ai_recommendation_history_id BIGSERIAL PRIMARY KEY,
    recommendation_type          VARCHAR(30) NOT NULL,
    product_id                   BIGINT REFERENCES product (product_id) ON DELETE CASCADE,
    sku_id                       BIGINT REFERENCES product_sku (sku_id) ON DELETE SET NULL,
    priority                     INT NOT NULL DEFAULT 0,
    suggested_quantity           INT,
    metrics_json                 TEXT NOT NULL,
    reason                       VARCHAR(2000) NOT NULL,
    provider                     VARCHAR(20) NOT NULL,
    ai_analysis_id               BIGINT REFERENCES ai_analysis_history (ai_analysis_id) ON DELETE SET NULL,
    status                       VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_by                   BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    decided_by                   BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    decided_at                   TIMESTAMPTZ,
    CONSTRAINT ck_ai_recommendation_type CHECK (recommendation_type IN ('PURCHASE', 'SLOW_MOVING')),
    CONSTRAINT ck_ai_recommendation_provider CHECK (provider IN ('RULE', 'OPENAI', 'MOCK')),
    CONSTRAINT ck_ai_recommendation_status CHECK (status IN ('OPEN', 'ACCEPTED', 'DISMISSED'))
);

CREATE INDEX idx_ai_recommendation_created ON ai_recommendation_history (recommendation_type, created_at DESC);
