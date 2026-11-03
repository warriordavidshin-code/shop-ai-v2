-- Purchase ledger (상품매입장부) mirroring the 매입장부 sheet of 여성복_월별_상품매입장부_영수증반영.xlsx,
-- its evidence files, AI analysis history, and product fields filled by AI product analysis.
-- Additive only: no existing table data is changed.

-- ------------------------------------------------------------------ AI analysis history / usage log

-- One row per AI call (or mock run). Successful rows double as a result cache keyed by input_hash
-- (hash of the input files + analysis type + prompt version), so re-uploading the same receipt does not
-- call the model again. result_json holds the validated structured result (no API key, no raw request).
CREATE TABLE ai_analysis_history (
    ai_analysis_id     BIGSERIAL PRIMARY KEY,
    analysis_type      VARCHAR(30) NOT NULL,
    target_type        VARCHAR(30),
    target_id          BIGINT,
    input_hash         VARCHAR(64) NOT NULL,
    input_file_count   INT NOT NULL DEFAULT 0,
    provider           VARCHAR(20) NOT NULL,
    model_name         VARCHAR(100),
    prompt_version     VARCHAR(20) NOT NULL,
    analysis_status    VARCHAR(20) NOT NULL,
    result_json        TEXT,
    error_code         VARCHAR(50),
    error_message      VARCHAR(500),
    input_tokens       INT,
    output_tokens      INT,
    estimated_cost_usd NUMERIC(12, 6),
    duration_ms        BIGINT,
    attempt_count      INT NOT NULL DEFAULT 1,
    requested_by       BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_ai_analysis_type CHECK (analysis_type IN ('RECEIPT', 'PRODUCT', 'PURCHASE_RECOMMENDATION')),
    CONSTRAINT ck_ai_analysis_provider CHECK (provider IN ('OPENAI', 'MOCK')),
    CONSTRAINT ck_ai_analysis_status CHECK (analysis_status IN ('SUCCEEDED', 'FAILED'))
);

CREATE INDEX idx_ai_analysis_cache ON ai_analysis_history (analysis_type, input_hash, created_at DESC)
    WHERE analysis_status = 'SUCCEEDED';
CREATE INDEX idx_ai_analysis_created ON ai_analysis_history (created_at DESC);
CREATE INDEX idx_ai_analysis_target ON ai_analysis_history (target_type, target_id);

-- ------------------------------------------------------------------ purchase ledger

-- Management numbers are YYYYMM-NNN, numbered per purchase month. This counter hands out the next number
-- atomically (INSERT ... ON CONFLICT DO UPDATE ... RETURNING), the unique index on management_no is the backstop.
CREATE TABLE purchase_ledger_sequence (
    year_month CHAR(6) PRIMARY KEY,
    last_seq   INT NOT NULL,
    CONSTRAINT ck_purchase_ledger_sequence_month CHECK (year_month ~ '^[0-9]{6}$'),
    CONSTRAINT ck_purchase_ledger_sequence_seq CHECK (last_seq > 0)
);

-- Columns follow the sheet order: 관리번호, 매입일, 거래처 상호, 사업자번호, 품목·수량 요약, 매입액, 지급액, 지급일,
-- 수취인·예금주, 이체 식별번호, 차액, 명세표 보관, 이체내역 보관, 적격증빙 종류, 증빙파일명·폴더, 비고, 자료 확인.
-- Amounts are whole won (BIGINT). difference_amount follows the sheet formula IF(F="","",F-G).
-- verification_status follows IF(OR(B,C,D,F,G,H,I blank, L<>"보관", M<>"보관"),"자료 확인 필요","기본자료 보관").
-- Rows are soft-deleted (deleted_at) so bookkeeping evidence is retained; numbers are never reused.
CREATE TABLE purchase_ledger (
    purchase_ledger_id   BIGSERIAL PRIMARY KEY,
    management_no        VARCHAR(20) NOT NULL,
    purchase_date        DATE NOT NULL,
    supplier_name        VARCHAR(200),
    supplier_business_no VARCHAR(20),
    item_summary         VARCHAR(4000),
    purchase_amount      BIGINT,
    payment_amount       BIGINT,
    payment_date         DATE,
    payment_method       VARCHAR(20),
    recipient_name       VARCHAR(100),
    transfer_reference   VARCHAR(100),
    difference_amount    BIGINT GENERATED ALWAYS AS (
        CASE WHEN purchase_amount IS NULL THEN NULL ELSE purchase_amount - COALESCE(payment_amount, 0) END
    ) STORED,
    statement_kept       VARCHAR(20) NOT NULL DEFAULT 'NOT_KEPT',
    transfer_kept        VARCHAR(20) NOT NULL DEFAULT 'NOT_KEPT',
    evidence_type        VARCHAR(20) NOT NULL DEFAULT 'NONE',
    evidence_location    VARCHAR(500),
    remarks              VARCHAR(4000),
    verification_status  VARCHAR(20) NOT NULL,
    entry_source         VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    source_analysis_id   BIGINT REFERENCES ai_analysis_history (ai_analysis_id) ON DELETE SET NULL,
    created_by           BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    updated_by           BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    deleted_at           TIMESTAMPTZ,
    deleted_by           BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version              BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_purchase_ledger_management_no UNIQUE (management_no),
    CONSTRAINT ck_purchase_ledger_management_no CHECK (management_no ~ '^[0-9]{6}-[0-9]{3,}$'),
    CONSTRAINT ck_purchase_ledger_amounts CHECK (
        (purchase_amount IS NULL OR purchase_amount >= 0) AND (payment_amount IS NULL OR payment_amount >= 0)
    ),
    CONSTRAINT ck_purchase_ledger_statement_kept CHECK (statement_kept IN ('KEPT', 'NOT_KEPT', 'NOT_APPLICABLE')),
    CONSTRAINT ck_purchase_ledger_transfer_kept CHECK (transfer_kept IN ('KEPT', 'NOT_KEPT', 'NOT_APPLICABLE')),
    CONSTRAINT ck_purchase_ledger_evidence_type CHECK (
        evidence_type IN ('NONE', 'TAX_INVOICE', 'CASH_RECEIPT', 'CARD_SLIP', 'INVOICE')
    ),
    CONSTRAINT ck_purchase_ledger_payment_method CHECK (
        payment_method IS NULL OR payment_method IN ('CASH', 'TRANSFER', 'CARD', 'OTHER')
    ),
    CONSTRAINT ck_purchase_ledger_verification CHECK (verification_status IN ('NEEDS_REVIEW', 'BASIC_COMPLETE')),
    CONSTRAINT ck_purchase_ledger_source CHECK (entry_source IN ('MANUAL', 'AI'))
);

CREATE INDEX idx_purchase_ledger_purchase_date ON purchase_ledger (purchase_date DESC, purchase_ledger_id DESC)
    WHERE deleted_at IS NULL;
CREATE INDEX idx_purchase_ledger_payment_date ON purchase_ledger (payment_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_purchase_ledger_supplier ON purchase_ledger (LOWER(supplier_name)) WHERE deleted_at IS NULL;
CREATE INDEX idx_purchase_ledger_verification ON purchase_ledger (verification_status) WHERE deleted_at IS NULL;
CREATE INDEX idx_purchase_ledger_transfer_ref ON purchase_ledger (transfer_reference)
    WHERE transfer_reference IS NOT NULL AND deleted_at IS NULL;

-- Line items of one statement / receipt; the ledger row keeps the summary and totals.
CREATE TABLE purchase_ledger_item (
    purchase_ledger_item_id BIGSERIAL PRIMARY KEY,
    purchase_ledger_id      BIGINT NOT NULL REFERENCES purchase_ledger (purchase_ledger_id) ON DELETE CASCADE,
    line_no                 INT NOT NULL,
    product_name            VARCHAR(200) NOT NULL,
    option_name             VARCHAR(200),
    quantity                INT,
    unit_price              BIGINT,
    total_amount            BIGINT,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_purchase_ledger_item_line UNIQUE (purchase_ledger_id, line_no),
    CONSTRAINT ck_purchase_ledger_item_values CHECK (
        (quantity IS NULL OR quantity >= 0) AND (unit_price IS NULL OR unit_price >= 0)
        AND (total_amount IS NULL OR total_amount >= 0)
    )
);

-- Evidence files live in a private directory (never under the public /uploads path) and are served only
-- through the admin API. A file uploaded for AI analysis has no ledger yet until the admin saves the entry.
CREATE TABLE purchase_evidence (
    purchase_evidence_id BIGSERIAL PRIMARY KEY,
    purchase_ledger_id   BIGINT REFERENCES purchase_ledger (purchase_ledger_id) ON DELETE CASCADE,
    original_file_name   VARCHAR(255) NOT NULL,
    stored_path          VARCHAR(500) NOT NULL,
    content_type         VARCHAR(100) NOT NULL,
    file_size            BIGINT NOT NULL,
    file_hash            VARCHAR(64) NOT NULL,
    evidence_category    VARCHAR(30) NOT NULL DEFAULT 'OTHER',
    ai_analysis_status   VARCHAR(20) NOT NULL DEFAULT 'NOT_ANALYZED',
    ai_analysis_id       BIGINT REFERENCES ai_analysis_history (ai_analysis_id) ON DELETE SET NULL,
    uploaded_by          BIGINT REFERENCES member (member_id) ON DELETE SET NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_purchase_evidence_path UNIQUE (stored_path),
    CONSTRAINT ck_purchase_evidence_size CHECK (file_size > 0),
    CONSTRAINT ck_purchase_evidence_hash CHECK (file_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_purchase_evidence_category CHECK (evidence_category IN (
        'STATEMENT', 'CASH_RECEIPT', 'CARD_RECEIPT', 'TRANSFER', 'TAX_INVOICE', 'OTHER'
    )),
    CONSTRAINT ck_purchase_evidence_ai_status CHECK (ai_analysis_status IN ('NOT_ANALYZED', 'ANALYZED', 'FAILED'))
);

CREATE INDEX idx_purchase_evidence_ledger ON purchase_evidence (purchase_ledger_id);
CREATE INDEX idx_purchase_evidence_hash ON purchase_evidence (file_hash);
-- The same file attached twice to one entry is always a mistake.
CREATE UNIQUE INDEX uq_purchase_evidence_ledger_hash ON purchase_evidence (purchase_ledger_id, file_hash)
    WHERE purchase_ledger_id IS NOT NULL;
CREATE INDEX idx_purchase_evidence_unlinked ON purchase_evidence (created_at) WHERE purchase_ledger_id IS NULL;

-- ------------------------------------------------------------------ product fields filled by AI analysis

ALTER TABLE product ADD COLUMN search_keywords VARCHAR(500);
ALTER TABLE product ADD COLUMN seo_description VARCHAR(300);
ALTER TABLE product ADD COLUMN purchase_price NUMERIC(15, 2);
ALTER TABLE product ADD CONSTRAINT ck_product_purchase_price CHECK (purchase_price IS NULL OR purchase_price >= 0);
