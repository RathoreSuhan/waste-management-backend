-- ---------------------------------------------------------------------------
-- Clean Bharat - remove UNIQUE rules the current entities no longer declare
-- ---------------------------------------------------------------------------
--
-- WHY THIS EXISTS
--
-- Hibernate's ddl-auto=update only ever *adds* to a live schema. When an entity
-- is replaced by a newer design, its columns stay behind and so do its UNIQUE
-- constraints and unique indexes.
--
-- cleanup_approvals grew out of two superseded entities (CleanupAuthorization
-- and MunicipalApproval), each of which stored a single decision row per
-- cleanup. Today the table is an append-only decision ledger: one row per
-- municipal decision, so the same assignment legitimately collects several rows
-- (for example REVISION_REQUIRED first, then REVISION_SUBMITTED, then APPROVED).
--
-- With the old rule still in place, every decision after the first failed with:
--
--   ERROR: duplicate key value violates unique constraint ...   (SQLSTATE 23505)
--
-- and the municipal dashboard showed "Decision not recorded - this could not be
-- saved because it conflicts with existing records" for Reject Proposal,
-- Request Revision and Approve & Assign.
--
-- StaleUniqueConstraintInitializer performs exactly these steps on every
-- startup, so this script is only needed for a manual repair (for example on a
-- database the application cannot alter, or to inspect before changing
-- anything). Run it with a user that owns the table.
--
-- Companion scripts: fix-enum-check-constraints.sql (widens the enum CHECK
-- lists) and relax-optional-columns.sql (drops stale NOT NULL rules).
--
-- SAFETY: only validation rules are removed, no row is ever touched, and the
-- primary key is deliberately left alone. Every statement is idempotent.
-- ---------------------------------------------------------------------------


-- ---------------------------------------------------------------------------
-- STEP 1 - read only: what does the live table actually enforce today?
-- ---------------------------------------------------------------------------
-- contype: p = primary key, u = unique, f = foreign key, c = check.
-- Anything with contype = 'u' below is a leftover, because CleanupApproval
-- declares @Table(name = "cleanup_approvals") with no uniqueConstraints.

SELECT con.conname                    AS constraint_name,
       con.contype                    AS constraint_type,
       pg_get_constraintdef(con.oid)  AS definition
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
WHERE rel.relname = 'cleanup_approvals'
  AND rel.relnamespace = 'public'::regnamespace
ORDER BY con.contype, con.conname;

-- Unique indexes that no constraint owns (Hibernate builds these for
-- @Column(unique = true), and they survive on their own once the mapping is gone).

SELECT index_class.relname AS index_name,
       pg_get_indexdef(idx.indexrelid) AS definition
FROM pg_index idx
         JOIN pg_class index_class ON index_class.oid = idx.indexrelid
         JOIN pg_class table_class ON table_class.oid = idx.indrelid
WHERE table_class.relname = 'cleanup_approvals'
  AND table_class.relnamespace = 'public'::regnamespace
  AND idx.indisunique
  AND NOT idx.indisprimary
  AND NOT EXISTS (SELECT 1
                  FROM pg_constraint con
                  WHERE con.conindid = idx.indexrelid);


-- ---------------------------------------------------------------------------
-- STEP 2 - drop the leftovers listed by STEP 1
-- ---------------------------------------------------------------------------
-- These are the names the two superseded entities used. Hibernate also invents
-- randomised names such as uk_9r8s2v..., so copy any extra name STEP 1 printed
-- into the same pattern below. DROP ... IF EXISTS keeps re-runs harmless.

ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS cleanup_approvals_assignment_id_key;          -- one decision per cleanup (old design)

ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS cleanup_approvals_assignment_id_stage_key;    -- one decision per cleanup stage (old design)

ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS cleanup_approvals_proposal_id_key;            -- one decision per proposal (old design)

ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS uk_cleanup_approvals_assignment;              -- Hibernate style name for the same rule

DROP INDEX IF EXISTS cleanup_approvals_assignment_id_key;                   -- bare index left by @Column(unique = true)

DROP INDEX IF EXISTS cleanup_approvals_proposal_id_key;                     -- bare index left by @Column(unique = true)


-- ---------------------------------------------------------------------------
-- STEP 3 - make sure the per-proposal link exists
-- ---------------------------------------------------------------------------
-- CleanupApproval.proposal records which plan a PROPOSAL-stage decision refers
-- to (it stays null for COMPLETION-stage decisions). The revision lock reads
-- this column, so a database created before the field existed needs it added.
-- Hibernate adds it automatically; this is here for a fully manual repair.

ALTER TABLE cleanup_approvals
    ADD COLUMN IF NOT EXISTS proposal_id BIGINT;                            -- nullable: completion decisions have no proposal

-- Point it at cleanup_proposals only if that link is not registered yet.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM pg_constraint con
                            JOIN pg_class rel ON rel.oid = con.conrelid
                   WHERE rel.relname = 'cleanup_approvals'
                     AND con.contype = 'f'
                     AND pg_get_constraintdef(con.oid) LIKE '%(proposal_id)%')
    THEN
        ALTER TABLE cleanup_approvals
            ADD CONSTRAINT fk_cleanup_approvals_proposal
                FOREIGN KEY (proposal_id) REFERENCES cleanup_proposals (id);
    END IF;
END $$;

-- A plain (non-unique) index keeps the "latest decision for this proposal"
-- lookup fast; it must never be unique, several decisions share one proposal.

CREATE INDEX IF NOT EXISTS idx_cleanup_approvals_proposal
    ON cleanup_approvals (proposal_id);


-- ---------------------------------------------------------------------------
-- STEP 4 - verify: re-run STEP 1. No contype = 'u' row should remain, the
-- primary key and the foreign keys must still be listed, and proposal_id must
-- appear in the column list below.
-- ---------------------------------------------------------------------------

SELECT column_name,
       is_nullable,
       data_type
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'cleanup_approvals'
ORDER BY ordinal_position;