-- =====================================================================
-- V15 rollback (AI cost control + personalized recommendation)
--
-- Manual use only. Flyway does NOT run files in docs/.
--  1. Stop the backend.
--  2. Back up first:  pg_dump -Fc -d shop_ai -f shop_ai_before_v15_rollback.dump
--  3. psql -d shop_ai -v ON_ERROR_STOP=1 -f V15__rollback.sql
--  4. Deploy an application build from before V15 (a V15 build will fail
--     "ddl-auto: validate" after this script).
--
-- Deleted: AI settings/usage/budget/billing records, product AI features,
-- view/search history, customer preferences, recommendation settings/rules,
-- precomputed rankings, recommendation daily stats, and recommendation
-- events that the new tracking wrote (rows without recommendation_id).
-- AI history rows written by Gemini / local OCR / product-feature analysis
-- are deleted because the old CHECK constraints do not allow them.
-- Orders, payments, products, stock and members are NOT changed.
-- =====================================================================

BEGIN;

-- ------------------------------------------------------------------ recommendation
DROP TABLE IF EXISTS recommendation_daily_stats;
DROP TABLE IF EXISTS product_recommendation;
DROP TABLE IF EXISTS recommendation_rule;
DROP TABLE IF EXISTS recommendation_settings;
DROP TABLE IF EXISTS customer_product_preference;
DROP TABLE IF EXISTS customer_search_history;
DROP TABLE IF EXISTS product_view_history;
DROP TABLE IF EXISTS product_ai_features;

DROP INDEX IF EXISTS idx_reco_event_created;
DROP INDEX IF EXISTS idx_reco_event_request;
DROP INDEX IF EXISTS uq_reco_event_dedupe;
DELETE FROM recommendation_event WHERE recommendation_id IS NULL OR event_type = 'IMPRESSION';
ALTER TABLE recommendation_event DROP CONSTRAINT IF EXISTS ck_reco_event_type;
ALTER TABLE recommendation_event ADD CONSTRAINT ck_reco_event_type
    CHECK (event_type IN ('VIEW', 'CLICK', 'CART', 'PURCHASE', 'SKIP'));
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS revenue;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS order_item_id;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS dedupe_key;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS item_count;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS session_key;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS member_id;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS variant;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS placement;
ALTER TABLE recommendation_event DROP COLUMN IF EXISTS request_id;
ALTER TABLE recommendation_event ALTER COLUMN recommendation_id SET NOT NULL;

ALTER TABLE member DROP COLUMN IF EXISTS personalization_enabled;

-- ------------------------------------------------------------------ AI cost control
DROP TABLE IF EXISTS ai_billing_record;
DROP TABLE IF EXISTS ai_budget_month;
DROP TABLE IF EXISTS ai_usage_daily;
DROP TABLE IF EXISTS ai_settings;

DELETE FROM ai_recommendation_history WHERE provider NOT IN ('RULE', 'OPENAI', 'MOCK');
ALTER TABLE ai_recommendation_history DROP CONSTRAINT IF EXISTS ck_ai_recommendation_provider;
ALTER TABLE ai_recommendation_history ADD CONSTRAINT ck_ai_recommendation_provider
    CHECK (provider IN ('RULE', 'OPENAI', 'MOCK'));

DELETE FROM ai_analysis_history
WHERE provider NOT IN ('OPENAI', 'MOCK')
   OR analysis_type NOT IN ('RECEIPT', 'PRODUCT', 'PURCHASE_RECOMMENDATION');
ALTER TABLE ai_analysis_history DROP CONSTRAINT IF EXISTS ck_ai_analysis_billing;
ALTER TABLE ai_analysis_history DROP COLUMN IF EXISTS billing_type;
ALTER TABLE ai_analysis_history DROP CONSTRAINT IF EXISTS ck_ai_analysis_type;
ALTER TABLE ai_analysis_history ADD CONSTRAINT ck_ai_analysis_type
    CHECK (analysis_type IN ('RECEIPT', 'PRODUCT', 'PURCHASE_RECOMMENDATION'));
ALTER TABLE ai_analysis_history DROP CONSTRAINT IF EXISTS ck_ai_analysis_provider;
ALTER TABLE ai_analysis_history ADD CONSTRAINT ck_ai_analysis_provider
    CHECK (provider IN ('OPENAI', 'MOCK'));

DELETE FROM flyway_schema_history WHERE version = '15';

COMMIT;
