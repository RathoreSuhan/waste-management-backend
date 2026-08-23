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
 * Removes UNIQUE rules that a live PostgreSQL schema still carries but the
 * current Java entities never ask for.
 *
 * Hibernate's ddl-auto=update only ever *adds* to an existing schema. When an
 * entity is replaced by a newer design, the columns it created stay, and so do
 * its UNIQUE constraints and unique indexes. cleanup_approvals grew out of two
 * superseded entities (CleanupAuthorization and MunicipalApproval), each of
 * which allowed a single decision row per cleanup. Today the table is an
 * append-only decision ledger: one row per municipal decision, so the same
 * assignment legitimately collects several rows.
 *
 * The leftover UNIQUE rule made every decision after the first one fail with:
 *
 *   ERROR: duplicate key value violates unique constraint ...   (SQLSTATE 23505)
 *
 * which GlobalExceptionHandler reports to the officer as
 * "Decision not recorded - this could not be saved because it conflicts with
 * existing records", so Reject Proposal and Request Revision both stopped
 * working once a site already had one decision on file.
 *
 * Safe by design, in the same spirit as ColumnNullabilityInitializer and
 * EnumCheckConstraintInitializer: it only ever removes a validation rule and
 * never touches a row; the primary key is always left in place; it is
 * idempotent, so it is a no-op on every later startup; it skips any database
 * that is not PostgreSQL (the H2 test schema is built fresh from today's
 * entities); and a failure is logged as a warning instead of stopping the
 * application.
 *
 * Set cleanbharat.db.drop-stale-unique-constraints=false to switch it off.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0) // Clear the stale rules before any startup data task writes rows
@ConditionalOnProperty(
        name = "cleanbharat.db.drop-stale-unique-constraints",
        havingValue = "true",
        matchIfMissing = true) // Enabled unless it is explicitly turned off
public class StaleUniqueConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Hard-coded names only, so nothing dynamic can ever reach the DDL below. */
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");

    /**
     * One entry per table whose entity declares no @Table(uniqueConstraints),
     * meaning every UNIQUE rule except the primary key is a leftover.
     */
    private record LedgerTable(String table, String reason) {
    }

    private static final List<LedgerTable> LEDGER_TABLES = List.of(
            // Append-only decision ledger: a site collects one row per municipal decision
            new LedgerTable("cleanup_approvals",
                    "municipal decisions are appended, so an assignment may hold many rows")
    );

    @Override
    public void run(String... args) {

        if (!isPostgres()) {
            return; // H2 / other databases build their schema from the current entities
        }

        int dropped = 0;

        for (LedgerTable ledgerTable : LEDGER_TABLES) {

            // Skip anything that is not a plain identifier we control
            if (!isSafeIdentifier(ledgerTable.table())) {
                log.warn("Skipping unsafe table reference {}", ledgerTable.table());
                continue;
            }

            if (!tableExists(ledgerTable.table())) {
                continue; // Nothing to repair on a database that has no such table yet
            }

            dropped += dropUniqueConstraints(ledgerTable);
            dropped += dropUniqueIndexes(ledgerTable);
        }

        if (dropped > 0) {
            log.info("Dropped {} stale UNIQUE rule(s) that the current entities no longer declare.", dropped);
        }
    }

    /** Only PostgreSQL keeps the stale rules; everything else is left alone. */
    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());

            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception ex) {
            log.warn("Could not read the database product name, skipping unique constraint repair: {}",
                    ex.getMessage());
            return false;
        }
    }

    /** Guards against altering a table that does not exist in this database. */
    private boolean tableExists(String table) {
        try {
            Integer found = jdbcTemplate.queryForObject("""
                            SELECT count(*)
                            FROM information_schema.tables
                            WHERE table_schema = 'public'
                              AND table_name = ?
                            """,
                    Integer.class, table);

            return found != null && found > 0;
        } catch (Exception ex) {
            log.warn("Could not inspect table {}: {}", table, ex.getMessage());
            return false;
        }
    }

    /** Drops every UNIQUE constraint on the table; contype 'u' never covers the primary key. */
    private int dropUniqueConstraints(LedgerTable ledgerTable) {

        List<String> constraints;

        try {
            constraints = jdbcTemplate.queryForList("""
                            SELECT con.conname
                            FROM pg_constraint con
                                     JOIN pg_class rel ON rel.oid = con.conrelid
                            WHERE con.contype = 'u'
                              AND con.connamespace = 'public'::regnamespace
                              AND rel.relname = ?
                            """,
                    String.class, ledgerTable.table());
        } catch (Exception ex) {
            log.warn("Could not list UNIQUE constraints on {}: {}", ledgerTable.table(), ex.getMessage());
            return 0;
        }

        int dropped = 0;

        for (String constraint : constraints) {

            if (!isSafeIdentifier(constraint)) {
                log.warn("Skipping unsafe constraint name {} on {}", constraint, ledgerTable.table());
                continue;
            }

            try {
                jdbcTemplate.execute("ALTER TABLE " + ledgerTable.table()
                        + " DROP CONSTRAINT IF EXISTS \"" + constraint + "\"");

                log.info("Dropped stale UNIQUE constraint {} on {} ({}).",
                        constraint, ledgerTable.table(), ledgerTable.reason());
                dropped++;
            } catch (Exception ex) {
                // Never stop the application over a constraint we could not drop
                log.warn("Could not drop UNIQUE constraint {} on {}: {}",
                        constraint, ledgerTable.table(), ex.getMessage());
            }
        }

        return dropped;
    }

    /**
     * Drops unique indexes that no constraint owns.
     *
     * Hibernate creates a bare unique index for @Column(unique = true), and such
     * an index survives on its own once the mapping is gone, so listing only
     * pg_constraint would miss it. Constraint-backed indexes are skipped here
     * because dropping the constraint above already removed them, and the
     * primary key index is never touched.
     */
    private int dropUniqueIndexes(LedgerTable ledgerTable) {

        List<String> indexes;

        try {
            indexes = jdbcTemplate.queryForList("""
                            SELECT index_class.relname
                            FROM pg_index idx
                                     JOIN pg_class index_class ON index_class.oid = idx.indexrelid
                                     JOIN pg_class table_class ON table_class.oid = idx.indrelid
                            WHERE table_class.relname = ?
                              AND table_class.relnamespace = 'public'::regnamespace
                              AND idx.indisunique
                              AND NOT idx.indisprimary
                              AND NOT EXISTS (SELECT 1
                                              FROM pg_constraint con
                                              WHERE con.conindid = idx.indexrelid)
                            """,
                    String.class, ledgerTable.table());
        } catch (Exception ex) {
            log.warn("Could not list unique indexes on {}: {}", ledgerTable.table(), ex.getMessage());
            return 0;
        }

        int dropped = 0;

        for (String index : indexes) {

            if (!isSafeIdentifier(index)) {
                log.warn("Skipping unsafe index name {} on {}", index, ledgerTable.table());
                continue;
            }

            try {
                jdbcTemplate.execute("DROP INDEX IF EXISTS \"" + index + "\"");

                log.info("Dropped stale unique index {} on {} ({}).",
                        index, ledgerTable.table(), ledgerTable.reason());
                dropped++;
            } catch (Exception ex) {
                // Never stop the application over an index we could not drop
                log.warn("Could not drop unique index {} on {}: {}",
                        index, ledgerTable.table(), ex.getMessage());
            }
        }

        return dropped;
    }

    /** Defensive check: the names come from the catalog, this keeps the DDL predictable. */
    private boolean isSafeIdentifier(String identifier) {
        return identifier != null && SAFE_IDENTIFIER.matcher(identifier).matches();
    }
}