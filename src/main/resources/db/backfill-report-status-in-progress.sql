-- One-off repair: reports stuck at PENDING while their cleanup is already live.
--
-- Why this is needed: startCleanup() used to move only the AssignmentStatus to
-- IN_PROGRESS and never touched the citizen's ReportStatus. Rows created before
-- that fix therefore read PENDING on the report page, in the public register and
-- on the citizen dashboard even though a cleaner is on site. The application code
-- now advances both axes together, so this script only repairs the old rows.
--
-- Safe to run more than once: the WHERE clause matches nothing after the first
-- run, RESOLVED reports are never touched, and no rows are inserted or deleted.

-- Reports whose cleanup is being worked on right now.
--
-- AWAITING_APPROVAL and REWORK_REQUIRED are included because the work is still
-- unfinished in both: the cleaner has submitted proof (or been sent back), and
-- only municipal sign-off may move the report on to RESOLVED.
UPDATE garbage_reports r
SET status = 'IN_PROGRESS'
WHERE r.status = 'PENDING'
  AND EXISTS (SELECT 1
              FROM cleanup_assignments a
              WHERE a.report_id = r.id
                AND a.status IN ('IN_PROGRESS', 'REWORK_REQUIRED', 'AWAITING_APPROVAL'));

-- Reports whose cleanup has been signed off but were left behind.
--
-- Defensive only: CleanupApprovalService already sets RESOLVED on approval. It
-- keeps a half-migrated database from showing a finished cleanup as PENDING.
UPDATE garbage_reports r
SET status = 'RESOLVED'
WHERE r.status <> 'RESOLVED'
  AND EXISTS (SELECT 1
              FROM cleanup_assignments a
              WHERE a.report_id = r.id
                AND a.status = 'COMPLETED');

-- Verification: every live cleanup should now report a non-PENDING status.
SELECT r.id            AS report_id,
       r.status        AS report_status,
       a.status        AS assignment_status,
       a.started_at    AS work_started_at
FROM garbage_reports r
         JOIN cleanup_assignments a ON a.report_id = r.id
WHERE a.status IN ('IN_PROGRESS', 'REWORK_REQUIRED', 'AWAITING_APPROVAL', 'COMPLETED')
ORDER BY r.id;