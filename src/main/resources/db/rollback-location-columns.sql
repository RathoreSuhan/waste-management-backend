-- Location refactor rollback: DROPS the five location columns, their indexes
-- and the backfill audit table.
--
--  *** READ BEFORE RUNNING - THIS SCRIPT IS DESTRUCTIVE ***
--
--  Application rollback NEVER needs this script: flipping
--  VITE_LOCATION_V2_ENABLED=false and/or redeploying the previous backend
--  artifact preserves these columns and every mapping written into them (plan
--  rule v5-1). The columns are additive and inert for the old code.
--
--  Run this script only in one of two situations:
--
--  (a) BEFORE any meaningful writes - i.e. before the LocationMigrationRunner
--      backfill boot and before any code-backed report or registration has
--      been created. All columns are still NULL, so this is genuinely
--      loss-free.
--
--  (b) AFTER mappings have been written: only with a fresh dedicated backup
--      (pg_dump / Neon snapshot) AND explicit operator approval. Dropping
--      populated columns DESTROYS those mappings; this is a recovery step,
--      never a routine rollback, and it is never "data-loss-free".
--
--  Every statement is idempotent.

DROP INDEX IF EXISTS uq_municipal_ulb_code;
DROP INDEX IF EXISTS idx_report_state_district;
DROP INDEX IF EXISTS idx_user_state_district;

ALTER TABLE municipal_corporations DROP COLUMN IF EXISTS ulb_code;
ALTER TABLE garbage_reports DROP COLUMN IF EXISTS ulb_code;
ALTER TABLE garbage_reports DROP COLUMN IF EXISTS district;
ALTER TABLE users DROP COLUMN IF EXISTS ulb_code;
ALTER TABLE users DROP COLUMN IF EXISTS district;

DROP TABLE IF EXISTS location_backfill_audit;

-- Verification: the columns and the audit table must be gone
SELECT table_name, column_name
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (('users', 'district'),
                                    ('users', 'ulb_code'),
                                    ('garbage_reports', 'district'),
                                    ('garbage_reports', 'ulb_code'),
                                    ('municipal_corporations', 'ulb_code'));

SELECT table_name FROM information_schema.tables
WHERE table_schema = 'public' AND table_name = 'location_backfill_audit';
