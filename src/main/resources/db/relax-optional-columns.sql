-- Manual fallback for ColumnNullabilityInitializer (PostgreSQL only).
--
-- The application drops these NOT NULL constraints automatically on every
-- startup, so this script is only needed when that runner is switched off
-- (cleanbharat.db.relax-optional-columns=false) or when a DBA has to repair a
-- database by hand.
--
-- Why it is needed at all: Hibernate's ddl-auto=update never relaxes an
-- existing NOT NULL. Once a field becomes optional in Java, an older database
-- keeps rejecting the inserts that are now legal, for example:
--
--   ERROR: null value in column "decided_by" of relation "cleanup_approvals"
--   violates not-null constraint          (SQLSTATE 23502)
--
-- Every statement below is idempotent and only removes a validation rule, so it
-- is safe to run more than once and it never touches data.

-- cleanup_approvals.decided_by
-- A Municipal Corporation reviews under its own official account, so there is no
-- separate officer User row to store. The corporation column is the authority of
-- record; decided_by only fills in if per-officer logins are added later.
-- Without this, Approve & Assign / Request Revision / Reject Proposal all fail
-- with "Decision not recorded" in the municipal dashboard.
ALTER TABLE cleanup_approvals
    ALTER COLUMN decided_by DROP NOT NULL;

-- Verification: is_nullable must read YES for every column listed above.
SELECT table_name,
       column_name,
       is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (('cleanup_approvals', 'decided_by'))
ORDER BY table_name, column_name;