-- =====================================================================
-- V14 rollback (supplier / receiving / cost / pricing / AI recommendation)
--
-- Manual use only. Flyway does NOT run files in docs/.
--  1. Stop the backend.
--  2. Back up first:  pg_dump -Fc -d shop_ai -f shop_ai_before_v14_rollback.dump
--  3. psql -d shop_ai -v ON_ERROR_STOP=1 -f V14__rollback.sql
--  4. Deploy an application build from before V14 (a V14 build will fail
--     "ddl-auto: validate" after this script).
--
-- Data in the tables created by V14 (suppliers, receipts, cost history,
-- pricing policy, price history, AI recommendations) is deleted.
-- product_sku.stock_quantity is NOT changed: stock already added by
-- confirmed receipts stays as it is.
-- =====================================================================

BEGIN;

-- AI purchase recommendations
DROP TABLE IF EXISTS ai_recommendation_history;

-- Sale cost snapshot
ALTER TABLE order_item DROP COLUMN IF EXISTS cost_method;
ALTER TABLE order_item DROP COLUMN IF EXISTS unit_cost;

-- Pricing
DROP TABLE IF EXISTS product_price_history;
DROP TABLE IF EXISTS product_pricing_policy;

-- Cost
DROP TABLE IF EXISTS product_cost_history;
DROP TABLE IF EXISTS sku_cost;

-- Inventory movement extensions
DROP INDEX IF EXISTS idx_inventory_movement_type_created;
DROP INDEX IF EXISTS idx_inventory_movement_created;
DROP INDEX IF EXISTS uq_inventory_movement_receipt_line;
-- V2 did not allow DAMAGE; keep the rows as adjustments.
UPDATE inventory_movement SET movement_type = 'ADJUSTMENT' WHERE movement_type = 'DAMAGE';
ALTER TABLE inventory_movement DROP CONSTRAINT IF EXISTS ck_movement_type;
ALTER TABLE inventory_movement ADD CONSTRAINT ck_movement_type CHECK (movement_type IN (
    'RECEIPT', 'SALE', 'CANCEL', 'RETURN', 'ADJUSTMENT', 'RESERVATION', 'RELEASE'
));
ALTER TABLE inventory_movement DROP COLUMN IF EXISTS unit_cost;
ALTER TABLE inventory_movement DROP COLUMN IF EXISTS purchase_receipt_id;
ALTER TABLE inventory_movement DROP COLUMN IF EXISTS stock_after;
ALTER TABLE inventory_movement DROP COLUMN IF EXISTS stock_before;
ALTER TABLE inventory_movement DROP COLUMN IF EXISTS product_id;

-- Receiving
DROP TABLE IF EXISTS purchase_receipt_item;
DROP TABLE IF EXISTS purchase_receipt;

-- Ledger item matching / receiving columns
DROP INDEX IF EXISTS idx_purchase_ledger_item_sku;
DROP INDEX IF EXISTS idx_purchase_ledger_item_match;
ALTER TABLE purchase_ledger_item DROP CONSTRAINT IF EXISTS ck_purchase_ledger_item_received;
ALTER TABLE purchase_ledger_item DROP CONSTRAINT IF EXISTS ck_purchase_ledger_item_match;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS received_quantity;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS match_note;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS match_score;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS matched_sku_id;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS matched_product_id;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS match_status;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS tax_amount;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS supply_amount;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS supplier_product_code;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS size;
ALTER TABLE purchase_ledger_item DROP COLUMN IF EXISTS color;

-- Ledger supplier / VAT columns
DROP INDEX IF EXISTS idx_purchase_ledger_supplier_id;
ALTER TABLE purchase_ledger DROP CONSTRAINT IF EXISTS ck_purchase_ledger_supply_tax;
ALTER TABLE purchase_ledger DROP COLUMN IF EXISTS tax_amount;
ALTER TABLE purchase_ledger DROP COLUMN IF EXISTS supply_amount;
ALTER TABLE purchase_ledger DROP COLUMN IF EXISTS supplier_id;

-- Suppliers
DROP TABLE IF EXISTS supplier_product_mapping;
DROP TABLE IF EXISTS supplier;

DELETE FROM flyway_schema_history WHERE version = '14';

COMMIT;
