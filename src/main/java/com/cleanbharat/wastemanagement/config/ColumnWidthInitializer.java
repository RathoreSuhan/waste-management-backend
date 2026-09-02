package com.cleanbharat.wastemanagement.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Widens PostgreSQL text columns that are narrower than the length the current
 * Java entities declare.
 *
 * Hibernate's ddl-auto=update only ever *adds* to a live schema. It creates
 * tables, columns and indexes, but it never changes the type of a column that
 * already exists. So the day a field is allowed to hold more text, the old
 * database keeps rejecting the inserts that are now perfectly legal.
 *
 * That is exactly why a citizen writing a long description saw their report
 * refused. garbage_reports.description was created as varchar(255) before
 * CreateReportRequest allowed 500 characters, so PostgreSQL answered:
 *
 *   ERROR: value too long for type character varying(255)   (SQLSTATE 22001)
 *
 * Spring wraps that as DataIntegrityViolationException, which the citizen was
 * shown as "This could not be saved because it conflicts with existing records"
 * - the duplicate-report wording, for a submission that was not a duplicate at
 * all. comments.message carried the same mismatch against the 1000 character
 * comment box.
 *
 * Safe by design, in the same spirit as ColumnNullabilityInitializer: it only
 * ever *widens*, so no value can stop fitting and no row is touched; widening a
 * varchar in PostgreSQL is a catalogue-only change with no table rewrite; it is
 * idempotent, so it is a no-op on every later startup; it skips any database
 * that is not PostgreSQL (other schemas are built fresh from today's entities);
 * and a failure is logged as a warning instead of stopping the application.
 *
 * Set cleanbharat.db.widen-text-columns=false to switch it off.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0) // Widen the columns before any startup data task tries to write rows
@ConditionalOnProperty(
        name = "cleanbharat.db.widen-text-columns",
        havingValue = "true",
        matchIfMissing = true) // Enabled unless it is explicitly turned off
public class ColumnWidthInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Hard-coded names only, so nothing dynamic can ever reach the DDL below. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    /**
     * One entry per column whose entity now declares a longer length than an
     * older database may have created. The reason is logged once, so the repair
     * is traceable.
     *
     * @param requiredLength length the entity declares today, never user input
     */
    private record TextColumn(String table, String column, int requiredLength, String reason) {
    }

    private static final List<TextColumn> TEXT_COLUMNS = List.of(

            // CreateReportRequest allows 500; the column predates that limit
            new TextColumn("garbage_reports", "description", 500,
                    "citizens may describe the waste in up to 500 characters"),

            // The discussion box and CommentRequest/ReplyRequest allow 1000
            new TextColumn("comments", "message", 1000,
                    "the discussion box accepts up to 1000 characters")
    );

    @Override
    public void run(String... args) {

        if (!isPostgres()) {
            return; // H2 / other databases build their schema from the current entities
        }

        int widened = 0;

        for (TextColumn textColumn : TEXT_COLUMNS) {

            // Skip anything that is not a plain lower-case identifier we control
            if (!isSafeIdentifier(textColumn.table()) || !isSafeIdentifier(textColumn.column())) {
                log.warn("Skipping unsafe column reference {}.{}",
                        textColumn.table(), textColumn.column());
                continue;
            }

            Integer liveWidth = readColumnWidth(textColumn);

            if (liveWidth == null) {
                continue; // Column absent, unreadable, or already an unbounded text column
            }

            if (liveWidth >= textColumn.requiredLength()) {
                continue; // Already wide enough in the database: nothing to do on this boot
            }

            try {
                // Only the two constants above reach this statement, never a request value
                jdbcTemplate.execute("ALTER TABLE " + textColumn.table()
                        + " ALTER COLUMN " + textColumn.column()
                        + " TYPE varchar(" + textColumn.requiredLength() + ")");

                log.info("Widened {}.{} from varchar({}) to varchar({}) ({}).",
                        textColumn.table(), textColumn.column(), liveWidth,
                        textColumn.requiredLength(), textColumn.reason());
                widened++;
            } catch (Exception ex) {
                // Never stop the application over a column we could not widen
                log.warn("Could not widen {}.{}: {}",
                        textColumn.table(), textColumn.column(), ex.getMessage());
            }
        }

        if (widened > 0) {
            log.info("Widened {} column(s) to the length the current entity mappings declare.", widened);
        }
    }

    /** Only PostgreSQL keeps the narrow column; everything else is left alone. */
    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());

            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception ex) {
            log.warn("Could not read the database product name, skipping column width repair: {}",
                    ex.getMessage());
            return false;
        }
    }

    /**
     * Reads the live maximum length of the column.
     *
     * @return the declared varchar length, or null when the column does not
     *         exist, could not be inspected, or is an unbounded type such as
     *         text - each of which means there is nothing to widen
     */
    private Integer readColumnWidth(TextColumn textColumn) {
        try {
            List<Integer> widths = jdbcTemplate.queryForList("""
                            SELECT character_maximum_length
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = ?
                              AND column_name = ?
                            """,
                    Integer.class, textColumn.table(), textColumn.column());

            if (widths.isEmpty()) {
                return null; // Nothing to widen on a schema that has no such column
            }

            // NULL here means an unbounded type (text), which already holds more than any varchar
            return widths.get(0);
        } catch (Exception ex) {
            log.warn("Could not inspect column {}.{}: {}",
                    textColumn.table(), textColumn.column(), ex.getMessage());
            return null;
        }
    }

    /** Defensive check: the names above are constants, this keeps them that way. */
    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }
}
