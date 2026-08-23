-- ============================================================================
-- Clean Bharat - manual repair script for stale enum CHECK constraints
-- ============================================================================
--
-- NOTE: THE APPLICATION NOW DOES THIS AUTOMATICALLY
-- -------------------------------------------------
-- config/EnumCheckConstraintInitializer runs the same repair on every startup,
-- rebuilding each list straight from the Java enum, so you normally do not need
-- to run anything by hand any more.
--
-- Keep this file for the cases the runner cannot cover: inspecting the current
-- constraints yourself, repairing a database the application cannot reach, or
-- when the repair has been switched off with
-- cleanbharat.db.repair-enum-constraints=false.
--
-- WHY THIS FILE EXISTS
-- --------------------
-- Submitting a cleanup proposal failed with:
--
--   ERROR: new row for relation "cleanup_assignments" violates check
--   constraint "cleanup_assignments_status_check"
--   Detail: Failing row contains (18, ..., PROPOSAL_SUBMITTED, 5, ..., 22, ...)
--
-- and again later on a different site:
--
--   Detail: Failing row contains (19, ..., PROPOSAL_SUBMITTED, 5, ..., 23, ...)
--
-- The second failure is why the repair was moved into the application: running
-- a script by hand is easy to forget, and every developer or deployment with
-- its own database hits the same wall.
--
-- The Java code is correct: AssignmentStatus already contains
-- PROPOSAL_SUBMITTED. The problem is only in the existing PostgreSQL database.
--
-- Hibernate creates a CHECK constraint for every @Enumerated(EnumType.STRING)
-- column, listing the enum values that existed at the moment the table was
-- first created. Later, when new statuses were added to the Java enums, the
-- schema was refreshed with ddl-auto=update - and "update" only ADDS tables,
-- columns and indexes. It never rewrites an existing CHECK constraint.
--
-- So the database is still validating rows against the OLD, shorter list of
-- statuses and rejects every new value (PROPOSAL_SUBMITTED today, and
-- ASSIGNED / AWAITING_APPROVAL / REWORK_REQUIRED on the next steps of the
-- workflow). The unit tests all pass because the test profile builds a brand
-- new H2 schema from the current enums every run.
--
-- HOW TO RUN (only if you are repairing a database by hand)
-- --------------------------------------------------------
--   pgAdmin : open the Clean Bharat database -> Query Tool -> paste this file
--             -> Execute
--   psql    : \i 'path/to/fix-enum-check-constraints.sql'
--
-- Then restart the Spring Boot application and retry the proposal form.
-- The script is safe to run more than once (DROP ... IF EXISTS everywhere) and
-- it does not touch or delete any row of data.
-- ============================================================================


-- ----------------------------------------------------------------------------
-- STEP 1 (optional, read-only): look at the CHECK constraints you have now.
-- Run this first if you want to see the stale value lists before fixing them,
-- and to spot any extra constraint whose name Hibernate randomised.
-- ----------------------------------------------------------------------------
SELECT rel.relname            AS table_name,      -- table the rule belongs to
       con.conname            AS constraint_name, -- name used in the error message
       pg_get_constraintdef(con.oid) AS definition -- the allowed value list
FROM pg_constraint con
         JOIN pg_class rel ON rel.oid = con.conrelid
WHERE con.contype = 'c'                            -- 'c' = CHECK constraint
  AND con.connamespace = 'public'::regnamespace     -- ignore system schemas
ORDER BY rel.relname, con.conname;


-- ----------------------------------------------------------------------------
-- STEP 2: replace each stale list with the full, current enum list.
-- Every block drops the old rule (if present) and adds it back with all the
-- values the Java enum defines today.
-- ----------------------------------------------------------------------------

-- cleanup_assignments.status -> enums/AssignmentStatus (this is the failing one)
ALTER TABLE cleanup_assignments
    DROP CONSTRAINT IF EXISTS cleanup_assignments_status_check;
ALTER TABLE cleanup_assignments
    ADD CONSTRAINT cleanup_assignments_status_check
        CHECK (status IN (
                          'PENDING',            -- open for cleaner proposals
                          'PROPOSAL_SUBMITTED', -- proposal waiting for municipal review
                          'ASSIGNED',           -- municipality authorised a cleaner
                          'IN_PROGRESS',        -- cleaner started on site
                          'AWAITING_APPROVAL',  -- proof submitted, final review pending
                          'REWORK_REQUIRED',    -- proof rejected, cleaner must redo
                          'CLAIMED',            -- legacy rows from the old direct-claim flow
                          'COMPLETED'           -- approved, reward + public feed released
            ));

-- cleanup_proposals.status -> enums/ProposalStatus
ALTER TABLE cleanup_proposals
    DROP CONSTRAINT IF EXISTS cleanup_proposals_status_check;
ALTER TABLE cleanup_proposals
    ADD CONSTRAINT cleanup_proposals_status_check
        CHECK (status IN (
                          'SUBMITTED',         -- fresh proposal from a cleaner
                          'APPROVED',          -- municipality picked this proposal
                          'REJECTED',          -- municipality turned it down
                          'REVISION_REQUIRED', -- cleaner asked to correct and resubmit
                          'WITHDRAWN'          -- cleaner pulled it back
            ));

-- cleanup_approvals.stage -> enums/ApprovalStage
ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS cleanup_approvals_stage_check;
ALTER TABLE cleanup_approvals
    ADD CONSTRAINT cleanup_approvals_stage_check
        CHECK (stage IN (
                         'PROPOSAL',  -- decision taken on the submitted plan
                         'COMPLETION' -- decision taken on the finished work
            ));

-- cleanup_approvals.decision -> enums/ApprovalDecision
ALTER TABLE cleanup_approvals
    DROP CONSTRAINT IF EXISTS cleanup_approvals_decision_check;
ALTER TABLE cleanup_approvals
    ADD CONSTRAINT cleanup_approvals_decision_check
        CHECK (decision IN (
                            'APPROVED',          -- officer accepted
                            'REJECTED',          -- officer refused
                            'REVISION_REQUIRED', -- officer sent it back for changes
                            'REVISION_SUBMITTED' -- cleaner returned the corrected plan (recorded by the system)
            ));

-- garbage_reports.status -> enums/ReportStatus
ALTER TABLE garbage_reports
    DROP CONSTRAINT IF EXISTS garbage_reports_status_check;
ALTER TABLE garbage_reports
    ADD CONSTRAINT garbage_reports_status_check
        CHECK (status IN (
                          'PENDING',     -- reported, nobody cleaning yet
                          'IN_PROGRESS', -- an authorised cleaner is working
                          'RESOLVED'     -- cleanup approved and closed
            ));

-- users.role -> enums/Role
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_role_check;
ALTER TABLE users
    ADD CONSTRAINT users_role_check
        CHECK (role IN (
                        'ROLE_ADMIN',             -- platform administrator
                        'ROLE_CITIZEN',           -- reports garbage
                        'ROLE_CLEANER',           -- proposes and performs cleanups
                        'ROLE_MUNICIPAL_OFFICER'  -- city corporation reviewer
            ));

-- users.cleaner_type -> enums/CleanerType (nullable: only cleaners carry one)
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS users_cleaner_type_check;
ALTER TABLE users
    ADD CONSTRAINT users_cleaner_type_check
        CHECK (cleaner_type IS NULL OR cleaner_type IN (
                                                        'INDIVIDUAL', -- single worker
                                                        'NGO',        -- non-profit organisation
                                                        'PRIVATE',    -- private contractor
                                                        'MUNICIPAL'   -- municipal staff
            ));


-- ----------------------------------------------------------------------------
-- STEP 3 (optional, read-only): re-run the STEP 1 query to confirm every list
-- above now shows the complete set of values.
--
-- If the STEP 1 output showed an extra constraint on one of these columns with
-- a random name (for example "cleanup_assignments_status_check1"), drop that
-- one too - a leftover duplicate keeps rejecting the new values:
--   ALTER TABLE cleanup_assignments DROP CONSTRAINT "<paste the exact name>";
-- ----------------------------------------------------------------------------