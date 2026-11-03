-- =====================================================================
-- V13 rollback (purchase ledger / evidence / AI analysis history /
-- product search keywords, SEO description, purchase price)
--
-- Manual use only. Flyway does NOT run files in docs/.
-- Run V14__rollback.sql first if V14 is applied.
--  1. Stop the backend.
--  2. Back up first:  pg_dump -Fc -d shop_ai -f shop_ai_before_v13_rollback.dump
--     and copy PRIVATE_UPLOAD_DIR (evidence files) somewhere safe.
--  3. psql -d shop_ai -v ON_ERROR_STOP=1 -f V13__rollback.sql
--  4. Deploy an application build from before V13.
--
-- All purchase ledger rows, evidence metadata and AI analysis history are
-- deleted. Evidence files in PRIVATE_UPLOAD_DIR are not touched by SQL.
-- product.search_keywords / seo_description / purchase_price are dropped.
-- =====================================================================

BEGIN;

ALTER TABLE product DROP CONSTRAINT IF EXISTS ck_product_purchase_price;
ALTER TABLE product DROP COLUMN IF EXISTS purchase_price;
ALTER TABLE product DROP COLUMN IF EXISTS seo_description;
ALTER TABLE product DROP COLUMN IF EXISTS search_keywords;

DROP TABLE IF EXISTS purchase_evidence;
DROP TABLE IF EXISTS purchase_ledger_item;
DROP TABLE IF EXISTS purchase_ledger;
DROP TABLE IF EXISTS purchase_ledger_sequence;
DROP TABLE IF EXISTS ai_analysis_history;

DELETE FROM flyway_schema_history WHERE version = '13';

COMMIT;
