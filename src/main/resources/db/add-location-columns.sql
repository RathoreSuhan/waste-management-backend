-- Location refactor (State → District → ULB): additive schema migration.
--
-- Companion of the plan's T1 step: run ONCE by the operator (psql / Neon SQL
-- editor) BEFORE the new backend artifact is deployed, so Hibernate's
-- ddl-auto=update finds every column already present and never mutates the
-- schema itself. The running old application ignores these columns entirely.
--
-- Every statement is idempotent and purely additive: no value is ever
-- overwritten, no existing column is altered, no row is touched. Rollback is
-- rollback-location-columns.sql - but note that it is DESTRUCTIVE once the new
-- backend has written mappings into these columns (see the rollback script and
-- the runbook's v5-1 rule).

-- ---------------------------------------------------------------------------
-- 1. Five new columns (users, garbage_reports, municipal_corporations)
-- ---------------------------------------------------------------------------

ALTER TABLE users ADD COLUMN IF NOT EXISTS district varchar(100);
ALTER TABLE users ADD COLUMN IF NOT EXISTS ulb_code varchar(20);

ALTER TABLE garbage_reports ADD COLUMN IF NOT EXISTS district varchar(100);
ALTER TABLE garbage_reports ADD COLUMN IF NOT EXISTS ulb_code varchar(20);

ALTER TABLE municipal_corporations ADD COLUMN IF NOT EXISTS ulb_code varchar(20);

-- ---------------------------------------------------------------------------
-- 2. Indexes (matching the entity declarations for fresh databases)
-- ---------------------------------------------------------------------------

-- Cleaner eligibility and the district leaderboard group on (state, district)
CREATE INDEX IF NOT EXISTS idx_user_state_district ON users (state, district);

-- Cleaner eligibility filters reports by (state, district)
CREATE INDEX IF NOT EXISTS idx_report_state_district ON garbage_reports (state, district);

-- One corporation per ULB: routing looks the destination up by ulb_code.
-- Safe to create here because the column was just added (all rows NULL, and
-- PostgreSQL treats NULLs as distinct in a unique index).
CREATE UNIQUE INDEX IF NOT EXISTS uq_municipal_ulb_code ON municipal_corporations (ulb_code);

-- ---------------------------------------------------------------------------
-- 3. Audit table for the backfill (LocationMigrationRunner)
--
-- One row per record the runner could NOT map automatically. This is the T5
-- reconciliation worklist; rows are never deleted by the application.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS location_backfill_audit (
    id          bigserial PRIMARY KEY,
    table_name  varchar(64)  NOT NULL,
    row_id      bigint       NOT NULL,
    city        varchar(100),
    state_value varchar(100),
    reason      varchar(255) NOT NULL,
    created_at  timestamp    NOT NULL DEFAULT now()
);

-- ---------------------------------------------------------------------------
-- Verification: the five columns, three indexes and the audit table must exist
-- ---------------------------------------------------------------------------

SELECT table_name, column_name, data_type, character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (('users', 'district'),
                                    ('users', 'ulb_code'),
                                    ('garbage_reports', 'district'),
                                    ('garbage_reports', 'ulb_code'),
                                    ('municipal_corporations', 'ulb_code'))
ORDER BY table_name, column_name;

SELECT indexname FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN ('idx_user_state_district', 'idx_report_state_district', 'uq_municipal_ulb_code')
ORDER BY indexname;
