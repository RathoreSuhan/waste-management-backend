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
 * Relaxes PostgreSQL NOT NULL constraints that the Java entities no longer require.
 *
 * Hibernate's ddl-auto=update only ever *adds* to a live schema. It creates
 * tables, columns and indexes, but it never widens an existing column and never
 * removes a NOT NULL that was written when the table was first created. So the
 * moment a field becomes optional in Java, the old database keeps rejecting the
 * inserts that are now perfectly legal.
 *
 * That is exactly why every municipal review decision was failing with:
 *
 *   ERROR: null value in column "decided_by" of relation "cleanup_approvals"
 *   violates not-null constraint            (SQLSTATE 23502)
 *
 * A Municipal Corporation signs in with its own official account, so there is no
 * separate officer User row to record in decided_by. CleanupApproval maps that
 * column as optional today, but the already-created table still demanded a value,
 * and the resulting DataIntegrityViolationException surfaced in the dashboard as
 * "Decision not recorded" for Approve & Assign, Request Revision and Reject.
 *
 * Safe by design, in the same spirit as EnumCheckConstraintInitializer: it only
 * ever removes a validation rule, never touches data; it is idempotent, so it is
 * a no-op on every later startup; it skips any database that is not PostgreSQL
 * (the H2 test schema is built fresh from today's entities); and a failure is
 * logged as a warning instead of stopping the application.
 *
 * Set cleanbharat.db.relax-optional-columns=false to switch it off.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0) // Relax the columns before any startup data task tries to write rows
@ConditionalOnProperty(
        name = "cleanbharat.db.relax-optional-columns",
        havingValue = "true",
        matchIfMissing = true) // Enabled unless it is explicitly turned off
public class ColumnNullabilityInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Hard-coded names only, so nothing dynamic can ever reach the DDL below. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    /**
     * One entry per column that is optional in Java but may still be NOT NULL in
     * an older database. The reason is logged once, so the repair is traceable.
     */
    private record OptionalColumn(String table, String column, String reason) {
    }

    private static final List<OptionalColumn> OPTIONAL_COLUMNS = List.of(
            // The corporation itself is the decider; there is no officer User row to store
            new OptionalColumn("cleanup_approvals", "decided_by",
                    "municipal corporations decide under their own account, so no officer user is recorded")
    );

    @Override
    public void run(String... args) {

        if (!isPostgres()) {
            return; // H2 / other databases build their schema from the current entities
        }

        int relaxed = 0;

        for (OptionalColumn optionalColumn : OPTIONAL_COLUMNS) {

            // Skip anything that is not a plain lower-case identifier we control
            if (!isSafeIdentifier(optionalColumn.table()) || !isSafeIdentifier(optionalColumn.column())) {
                log.warn("Skipping unsafe column reference {}.{}",
                        optionalColumn.table(), optionalColumn.column());
                continue;
            }

            Boolean nullable = readNullability(optionalColumn);

            if (nullable == null) {
                continue; // Column not present yet (fresh database, renamed field) or unreadable
            }

            if (nullable) {
                continue; // Already optional in the database: nothing to do on this boot
            }

            try {
                jdbcTemplate.execute("ALTER TABLE " + optionalColumn.table()
                        + " ALTER COLUMN " + optionalColumn.column() + " DROP NOT NULL");

                log.info("Dropped NOT NULL on {}.{} ({}).",
                        optionalColumn.table(), optionalColumn.column(), optionalColumn.reason());
                relaxed++;
            } catch (Exception ex) {
                // Never stop the application over a constraint we could not relax
                log.warn("Could not drop NOT NULL on {}.{}: {}",
                        optionalColumn.table(), optionalColumn.column(), ex.getMessage());
            }
        }

        if (relaxed > 0) {
            log.info("Relaxed {} column constraint(s) to match the current entity mappings.", relaxed);
        }
    }

    /** Only PostgreSQL keeps the stale NOT NULL; everything else is left alone. */
    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());

            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception ex) {
            log.warn("Could not read the database product name, skipping column nullability repair: {}",
                    ex.getMessage());
            return false;
        }
    }

    /**
     * Reads the live nullability of the column.
     *
     * @return TRUE when already nullable, FALSE when still NOT NULL,
     *         null when the column does not exist or could not be inspected
     */
    private Boolean readNullability(OptionalColumn optionalColumn) {
        try {
            List<String> isNullable = jdbcTemplate.queryForList("""
                            SELECT is_nullable
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = ?
                              AND column_name = ?
                            """,
                    String.class, optionalColumn.table(), optionalColumn.column());

            if (isNullable.isEmpty()) {
                return null; // Nothing to relax on a schema that has no such column
            }

            return "YES".equalsIgnoreCase(isNullable.get(0));
        } catch (Exception ex) {
            log.warn("Could not inspect column {}.{}: {}",
                    optionalColumn.table(), optionalColumn.column(), ex.getMessage());
            return null;
        }
    }

    /** Defensive check: the names above are constants, this keeps them that way. */
    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }
}