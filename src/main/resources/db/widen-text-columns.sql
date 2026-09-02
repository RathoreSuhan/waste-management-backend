-- Manual fallback for ColumnWidthInitializer (PostgreSQL only).
--
-- The application widens these columns automatically on every startup, so this
-- script is only needed when that runner is switched off
-- (cleanbharat.db.widen-text-columns=false) or when a DBA has to repair a
-- database by hand.
--
-- Why it is needed at all: Hibernate's ddl-auto=update never changes the type of
-- a column that already exists. Once a field is allowed to hold more text in
-- Java, an older database keeps rejecting the inserts that are now legal, for
-- example:
--
--   ERROR: value too long for type character varying(255)   (SQLSTATE 22001)
--
-- Every statement below only ever *widens*, so no stored value can stop fitting.
-- Widening a varchar is a catalogue-only change in PostgreSQL: no table rewrite
-- and no data touched. The guards make it idempotent and stop a column that was
-- already changed to unbounded `text` from being narrowed back to a varchar.

DO
$$
    BEGIN

        -- garbage_reports.description
        -- CreateReportRequest and the citizen report form both accept 500
        -- characters, but the table was created when the field had no limit and
        -- so is still varchar(255). Without this, a long description fails with
        -- "This could not be saved because it conflicts with existing records".
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'garbage_reports'
                     AND column_name = 'description'
                     AND character_maximum_length < 500) THEN

            ALTER TABLE garbage_reports
                ALTER COLUMN description TYPE varchar(500);
        END IF;

        -- comments.message
        -- The discussion box and CommentRequest/ReplyRequest allow 1000
        -- characters, while the column was created as varchar(255).
        IF EXISTS (SELECT 1
                   FROM information_schema.columns
                   WHERE table_schema = 'public'
                     AND table_name = 'comments'
                     AND column_name = 'message'
                     AND character_maximum_length < 1000) THEN

            ALTER TABLE comments
                ALTER COLUMN message TYPE varchar(1000);
        END IF;

    END
$$;

-- Verification: character_maximum_length must read 500 and 1000 respectively
-- (or NULL, if the column was already changed to unbounded text).
SELECT table_name,
       column_name,
       character_maximum_length
FROM information_schema.columns
WHERE table_schema = 'public'
  AND (table_name, column_name) IN (('garbage_reports', 'description'),
                                    ('comments', 'message'))
ORDER BY table_name, column_name;
