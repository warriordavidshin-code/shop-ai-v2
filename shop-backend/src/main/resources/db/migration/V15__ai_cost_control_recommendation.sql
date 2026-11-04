-- AI provider routing / cost control (FREE_FIRST) and the product recommendation system.
-- Additive only: no existing rows are deleted; constraints are widened, new columns are nullable or defaulted.
-- Manual rollback script: docs/db-rollback/V15__rollback.sql

-- ------------------------------------------------------------------ AI providers / cost control

ALTER TABLE ai_analysis_history DROP CONSTRAINT ck_ai_analysis_provider;
ALTER TABLE ai_analysis_history ADD CONSTRAINT ck_ai_analysis_provider
    CHECK (provider IN ('OPENAI', 'MOCK', 'GEMINI', 'LOCAL_OCR'));
ALTER TABLE ai_analysis_history DROP CONSTRAINT ck_ai_analysis_type;
ALTER TABLE ai_analysis_history ADD CONSTRAINT ck_ai_analysis_type
    CHECK (analysis_type IN ('RECEIPT', 'PRODUCT', 'PURCHASE_RECOMMENDATION', 'PRODUCT_FEATURE'));

-- FREE = free-tier external API, PAID = billed API, LOCAL = on-server OCR/rules, NONE = mock/test.
ALTER TABLE ai_analysis_history ADD COLUMN billing_type VARCHAR(10) NOT NULL DEFAULT 'PAID';
UPDATE ai_analysis_history SET billing_type = 'NONE' WHERE provider = 'MOCK';
ALTER TABLE ai_analysis_history ADD CONSTRAINT ck_ai_analysis_billing
    CHECK (billing_type IN ('FREE', 'PAID', 'LOCAL', 'NONE'));

ALTER TABLE ai_recommendation_history DROP CONSTRAINT ck_ai_recommendation_provider;
ALTER TABLE ai_recommendation_history ADD CONSTRAINT ck_ai_recommendation_provider
    CHECK (provider IN ('RULE', 'OPENAI', 'MOCK', 'GEMINI'));

-- Admin overrides of the AI_* environment defaults. NULL means "use the environment value".
-- API keys are never stored here.
CREATE TABLE ai_settings (
    settings_id           SMALLINT PRIMARY KEY DEFAULT 1,
    ai_mode               VARCHAR(20),
    local_ocr_enabled     BOOLEAN,
    gemini_enabled        BOOLEAN,
    openai_enabled        BOOLEAN,
    paid_fallback_enabled BOOLEAN,
    cache_enabled         BOOLEAN,
    daily_request_limit   INT,
    monthly_budget_usd    NUMERIC(10, 4),
    updated_by            BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_ai_settings_singleton CHECK (settings_id = 1),
    CONSTRAINT ck_ai_settings_mode CHECK (ai_mode IS NULL
        OR ai_mode IN ('FREE_FIRST', 'GEMINI', 'OPENAI', 'LOCAL_ONLY', 'MANUAL')),
    CONSTRAINT ck_ai_settings_limits CHECK ((daily_request_limit IS NULL OR daily_request_limit >= 0)
        AND (monthly_budget_usd IS NULL OR monthly_budget_usd >= 0))
);
INSERT INTO ai_settings (settings_id) VALUES (1);

-- Per-day counters. provider = 'EXTERNAL' is the gate row for AI_DAILY_REQUEST_LIMIT (all external API calls);
-- it is incremented with a conditional UPDATE so concurrent requests cannot exceed the limit.
CREATE TABLE ai_usage_daily (
    usage_date         DATE NOT NULL,
    provider           VARCHAR(20) NOT NULL,
    request_count      INT NOT NULL DEFAULT 0,
    success_count      INT NOT NULL DEFAULT 0,
    failure_count      INT NOT NULL DEFAULT 0,
    cache_hits         INT NOT NULL DEFAULT 0,
    blocked_count      INT NOT NULL DEFAULT 0,
    input_tokens       BIGINT NOT NULL DEFAULT 0,
    output_tokens      BIGINT NOT NULL DEFAULT 0,
    estimated_cost_usd NUMERIC(12, 6) NOT NULL DEFAULT 0,
    quota_exhausted_at TIMESTAMPTZ,
    PRIMARY KEY (usage_date, provider),
    CONSTRAINT ck_ai_usage_daily_provider CHECK (provider IN ('OPENAI', 'GEMINI', 'LOCAL_OCR', 'MOCK', 'EXTERNAL')),
    CONSTRAINT ck_ai_usage_daily_counts CHECK (request_count >= 0 AND cache_hits >= 0)
);

-- Monthly paid-API budget. A paid call first reserves its worst-case cost with
--   UPDATE ... SET reserved_usd = reserved_usd + :max WHERE spent_usd + reserved_usd + :max <= :budget
-- and settles the actual cost afterwards, so parallel requests cannot overspend the budget.
CREATE TABLE ai_budget_month (
    budget_month  CHAR(7) PRIMARY KEY,
    spent_usd     NUMERIC(12, 6) NOT NULL DEFAULT 0,
    reserved_usd  NUMERIC(12, 6) NOT NULL DEFAULT 0,
    blocked_count INT NOT NULL DEFAULT 0,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ai_budget_month_format CHECK (budget_month ~ '^[0-9]{4}-[0-9]{2}$'),
    CONSTRAINT ck_ai_budget_month_amounts CHECK (spent_usd >= 0 AND reserved_usd >= 0)
);

-- Actual invoices entered by an admin from the provider's billing page (kept apart from estimates).
CREATE TABLE ai_billing_record (
    billing_id    BIGSERIAL PRIMARY KEY,
    provider      VARCHAR(20) NOT NULL,
    billing_month CHAR(7) NOT NULL,
    amount_usd    NUMERIC(12, 4) NOT NULL,
    memo          VARCHAR(300),
    recorded_by   BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    recorded_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ai_billing_provider CHECK (provider IN ('OPENAI', 'GEMINI')),
    CONSTRAINT ck_ai_billing_month CHECK (billing_month ~ '^[0-9]{4}-[0-9]{2}$'),
    CONSTRAINT ck_ai_billing_amount CHECK (amount_usd >= 0)
);
CREATE INDEX idx_ai_billing_month ON ai_billing_record (billing_month DESC);

-- ------------------------------------------------------------------ recommendation: data

ALTER TABLE member ADD COLUMN personalization_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Product traits used by the matching algorithms. Filled once per product (rules, or AI when an admin asks);
-- customer page views never call an AI API.
CREATE TABLE product_ai_features (
    product_id      BIGINT PRIMARY KEY REFERENCES product (product_id) ON DELETE CASCADE,
    category_group  VARCHAR(20) NOT NULL,
    item_type       VARCHAR(30),
    colors          VARCHAR(300),
    color_tone      VARCHAR(20),
    style_tags      VARCHAR(300),
    design_tags     VARCHAR(300),
    season          VARCHAR(30),
    fit             VARCHAR(50),
    material        VARCHAR(100),
    keywords        VARCHAR(500),
    source          VARCHAR(20) NOT NULL,
    ai_provider     VARCHAR(20),
    ai_analysis_id  BIGINT REFERENCES ai_analysis_history (ai_analysis_id) ON DELETE SET NULL,
    feature_version VARCHAR(20) NOT NULL,
    analyzed_at     TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_product_features_group CHECK (category_group IN ('TOP', 'BOTTOM', 'DRESS', 'OUTER', 'ETC')),
    CONSTRAINT ck_product_features_source CHECK (source IN ('RULE', 'AI', 'MANUAL'))
);

-- Product detail views. Guests are identified only by a random cookie id (no IP, no user agent stored).
CREATE TABLE product_view_history (
    view_id     BIGSERIAL PRIMARY KEY,
    product_id  BIGINT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    member_id   BIGINT REFERENCES member (member_id) ON DELETE CASCADE,
    session_key VARCHAR(64),
    viewed_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_product_view_viewer CHECK (member_id IS NOT NULL OR session_key IS NOT NULL)
);
CREATE INDEX idx_product_view_product ON product_view_history (product_id, viewed_at DESC);
CREATE INDEX idx_product_view_member ON product_view_history (member_id, viewed_at DESC) WHERE member_id IS NOT NULL;
CREATE INDEX idx_product_view_session ON product_view_history (session_key, viewed_at DESC) WHERE session_key IS NOT NULL;

-- Recent search keywords per viewer (personalization only; purged with view history).
CREATE TABLE customer_search_history (
    search_id   BIGSERIAL PRIMARY KEY,
    member_id   BIGINT REFERENCES member (member_id) ON DELETE CASCADE,
    session_key VARCHAR(64),
    keyword     VARCHAR(100) NOT NULL,
    searched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_customer_search_viewer CHECK (member_id IS NOT NULL OR session_key IS NOT NULL)
);
CREATE INDEX idx_customer_search_member ON customer_search_history (member_id, searched_at DESC) WHERE member_id IS NOT NULL;
CREATE INDEX idx_customer_search_session ON customer_search_history (session_key, searched_at DESC) WHERE session_key IS NOT NULL;

-- Computed taste profile per member (JSON maps of normalized 0..1 scores).
CREATE TABLE customer_product_preference (
    member_id       BIGINT PRIMARY KEY REFERENCES member (member_id) ON DELETE CASCADE,
    category_scores TEXT NOT NULL DEFAULT '{}',
    color_scores    TEXT NOT NULL DEFAULT '{}',
    style_scores    TEXT NOT NULL DEFAULT '{}',
    price_min       NUMERIC(15, 2),
    price_max       NUMERIC(15, 2),
    price_avg       NUMERIC(15, 2),
    recent_keywords VARCHAR(500),
    signal_count    INT NOT NULL DEFAULT 0,
    computed_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ------------------------------------------------------------------ recommendation: settings / rules

CREATE TABLE recommendation_settings (
    settings_id         SMALLINT PRIMARY KEY DEFAULT 1,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    home_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    detail_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    cart_enabled        BOOLEAN NOT NULL DEFAULT TRUE,
    mypage_enabled      BOOLEAN NOT NULL DEFAULT TRUE,
    display_count       INT NOT NULL DEFAULT 8,
    cache_minutes       INT NOT NULL DEFAULT 30,
    min_stock           INT NOT NULL DEFAULT 1,
    attribution_days    INT NOT NULL DEFAULT 7,
    popular_days        INT NOT NULL DEFAULT 7,
    w_category          NUMERIC(4, 3) NOT NULL DEFAULT 0.250,
    w_color             NUMERIC(4, 3) NOT NULL DEFAULT 0.150,
    w_price             NUMERIC(4, 3) NOT NULL DEFAULT 0.150,
    w_purchase          NUMERIC(4, 3) NOT NULL DEFAULT 0.200,
    w_popularity        NUMERIC(4, 3) NOT NULL DEFAULT 0.150,
    w_freshness         NUMERIC(4, 3) NOT NULL DEFAULT 0.100,
    max_discount_rate   NUMERIC(5, 4) NOT NULL DEFAULT 0.3000,
    updated_by          BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT ck_reco_settings_singleton CHECK (settings_id = 1),
    CONSTRAINT ck_reco_settings_ranges CHECK (display_count BETWEEN 1 AND 30 AND cache_minutes BETWEEN 0 AND 1440
        AND min_stock >= 0 AND attribution_days BETWEEN 1 AND 90 AND popular_days BETWEEN 1 AND 90
        AND max_discount_rate >= 0 AND max_discount_rate < 1),
    CONSTRAINT ck_reco_settings_weights CHECK (w_category >= 0 AND w_color >= 0 AND w_price >= 0
        AND w_purchase >= 0 AND w_popularity >= 0 AND w_freshness >= 0)
);
INSERT INTO recommendation_settings (settings_id) VALUES (1);

-- COORDINATION: source_value + target_value are category groups (TOP, BOTTOM, ...) or item types (니트, 슬랙스, ...).
-- EXCLUDE_PRODUCT: product_id never recommended. CATEGORY_BOOST: source_value (group/item) gets weight x.
CREATE TABLE recommendation_rule (
    rule_id      BIGSERIAL PRIMARY KEY,
    rule_type    VARCHAR(20) NOT NULL,
    source_value VARCHAR(50),
    target_value VARCHAR(50),
    product_id   BIGINT REFERENCES product (product_id) ON DELETE CASCADE,
    weight       NUMERIC(4, 2) NOT NULL DEFAULT 1.00,
    memo         VARCHAR(200),
    active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_by   BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_reco_rule_type CHECK (rule_type IN ('COORDINATION', 'EXCLUDE_PRODUCT', 'CATEGORY_BOOST')),
    CONSTRAINT ck_reco_rule_weight CHECK (weight >= 0 AND weight <= 5),
    CONSTRAINT ck_reco_rule_fields CHECK (
        (rule_type = 'COORDINATION' AND source_value IS NOT NULL AND target_value IS NOT NULL)
        OR (rule_type = 'EXCLUDE_PRODUCT' AND product_id IS NOT NULL)
        OR (rule_type = 'CATEGORY_BOOST' AND source_value IS NOT NULL))
);
CREATE INDEX idx_reco_rule_type ON recommendation_rule (rule_type, active);

INSERT INTO recommendation_rule (rule_type, source_value, target_value, weight, memo) VALUES
    ('COORDINATION', '니트', '슬랙스', 1.00, '기본 코디 규칙'),
    ('COORDINATION', '블라우스', '스커트', 1.00, '기본 코디 규칙'),
    ('COORDINATION', '원피스', '가디건', 1.00, '기본 코디 규칙'),
    ('COORDINATION', '셔츠', '데님팬츠', 1.00, '기본 코디 규칙'),
    ('COORDINATION', 'TOP', 'BOTTOM', 0.80, '상의 + 하의'),
    ('COORDINATION', 'DRESS', 'OUTER', 0.80, '원피스 + 아우터'),
    ('COORDINATION', 'TOP', 'OUTER', 0.50, '상의 + 아우터'),
    ('COORDINATION', 'BOTTOM', 'OUTER', 0.40, '하의 + 아우터');

-- Precomputed ranking lists (popular / trending / new-popular), refreshed by a scheduler.
CREATE TABLE product_recommendation (
    list_type   VARCHAR(30) NOT NULL,
    period_days INT NOT NULL,
    rank_no     INT NOT NULL,
    product_id  BIGINT NOT NULL REFERENCES product (product_id) ON DELETE CASCADE,
    score       NUMERIC(10, 6) NOT NULL,
    breakdown   TEXT NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (list_type, period_days, rank_no)
);

-- ------------------------------------------------------------------ recommendation: tracking

-- recommendation_event (V4) now also stores events of the new recommendation areas, identified by a signed
-- request id; the old AI stylist events keep their recommendation_id.
ALTER TABLE recommendation_event ALTER COLUMN recommendation_id DROP NOT NULL;
ALTER TABLE recommendation_event ADD COLUMN request_id VARCHAR(80);
ALTER TABLE recommendation_event ADD COLUMN placement VARCHAR(40);
ALTER TABLE recommendation_event ADD COLUMN variant VARCHAR(10);
ALTER TABLE recommendation_event ADD COLUMN member_id BIGINT REFERENCES member (member_id) ON DELETE SET NULL;
ALTER TABLE recommendation_event ADD COLUMN session_key VARCHAR(64);
ALTER TABLE recommendation_event ADD COLUMN item_count INT;
ALTER TABLE recommendation_event ADD COLUMN dedupe_key VARCHAR(160);
-- PURCHASE events are written by the attribution job (one per paid order line) with the line revenue.
ALTER TABLE recommendation_event ADD COLUMN order_item_id BIGINT REFERENCES order_item (order_item_id) ON DELETE SET NULL;
ALTER TABLE recommendation_event ADD COLUMN revenue NUMERIC(15, 2);
ALTER TABLE recommendation_event DROP CONSTRAINT ck_reco_event_type;
ALTER TABLE recommendation_event ADD CONSTRAINT ck_reco_event_type
    CHECK (event_type IN ('VIEW', 'CLICK', 'CART', 'PURCHASE', 'SKIP', 'IMPRESSION'));
CREATE UNIQUE INDEX uq_reco_event_dedupe ON recommendation_event (dedupe_key) WHERE dedupe_key IS NOT NULL;
CREATE INDEX idx_reco_event_request ON recommendation_event (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_reco_event_created ON recommendation_event (created_at, placement);

CREATE TABLE recommendation_daily_stats (
    stat_date   DATE NOT NULL,
    placement   VARCHAR(40) NOT NULL,
    variant     VARCHAR(10) NOT NULL DEFAULT 'A',
    impressions INT NOT NULL DEFAULT 0,
    clicks      INT NOT NULL DEFAULT 0,
    carts       INT NOT NULL DEFAULT 0,
    purchases   INT NOT NULL DEFAULT 0,
    revenue     NUMERIC(15, 2) NOT NULL DEFAULT 0,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (stat_date, placement, variant)
);
