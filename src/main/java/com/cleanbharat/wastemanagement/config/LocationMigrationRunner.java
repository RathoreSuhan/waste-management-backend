package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService.NameMatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Operator-controlled backfill for the location refactor (plan phase T1 step
 * 4, rules v4-2 and v6-2).
 *
 * WHY IT EXISTS
 * -------------
 * Rows that predate the district/ULB model carry only state + city. This
 * runner gives each of those rows its directory district and ULB code so the
 * fail-closed eligibility and the district leaderboard have something to
 * match on - and it hands every row it cannot map, with the exact reason, to
 * the location_backfill_audit table, which is the operator's reconciliation
 * worklist.
 *
 * HOW IT RUNS
 * -----------
 * NEVER during ordinary startup: the bean exists only when
 * cleanbharat.db.location-migration.enabled=true is set explicitly, for one
 * boot, by the operator, after the additive DDL (db/add-location-columns.sql)
 * has been applied. On any later boot the property is absent and this class
 * is never even constructed. It also verifies the columns exist before
 * writing anything and aborts loudly rather than guessing if they do not.
 *
 * WHAT IT TOUCHES
 * ---------------
 * users and garbage_reports ONLY (rule v6-2). Both carry a persisted state,
 * so the name match stays inside the row's own state. municipal_corporations
 * is deliberately never written by this runner: its rows have no state
 * column, so a city-name match alone could bind a corporation to the wrong
 * state's ULB - those rows are mapped through the verified admin cascade or
 * an operator-reviewed UPDATE at T3 instead.
 *
 * WHAT IT NEVER DOES
 * ------------------
 * It never overwrites a value: only rows whose district is still NULL are
 * read, and every UPDATE repeats that guard in its WHERE clause. It never
 * rewrites city or state. It never invents an alias or resolves an ambiguity:
 * a city that matches no directory ULB name exactly, or one listed under
 * several districts of the same state, stays unmapped and is audited.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "cleanbharat.db.location-migration.enabled",
        havingValue = "true") // No matchIfMissing: absent property = never runs
public class LocationMigrationRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final IndianLocationService indianLocationService;

    /** Audit source values are constants; the city/state come from the row. */
    private static final String TABLE_USERS = "users";
    private static final String TABLE_REPORTS = "garbage_reports";

    @Override
    public void run(String... args) {

        if (!isPostgres()) {
            log.warn("Location backfill skipped: the database is not PostgreSQL.");
            return;
        }

        if (!requiredColumnsExist()) {
            // Abort loudly: a half-migrated schema must not be written to
            log.error("Location backfill ABORTED: run db/add-location-columns.sql first - "
                    + "one or more required columns/the audit table are missing.");
            return;
        }

        log.info("Location backfill starting (one-off operator run; scope: users + garbage_reports only).");

        Set<String> alreadyAudited = existingAuditKeys();

        int usersMapped = backfillTable(TABLE_USERS, alreadyAudited);
        int reportsMapped = backfillTable(TABLE_REPORTS, alreadyAudited);

        log.info("Location backfill finished: {} user row(s) and {} report row(s) mapped; "
                        + "audit rows now hold the unmapped remainder for manual reconciliation (T5). "
                        + "municipal_corporations was NOT touched (v6-2): map it via the admin cascade "
                        + "or a reviewed UPDATE during the T3 preflight.",
                usersMapped, reportsMapped);
    }

    /**
     * Maps every not-yet-mapped row of one table and returns how many rows
     * were mapped.
     */
    private int backfillTable(String tableName, Set<String> alreadyAudited) {

        List<BackfillRow> rows = jdbcTemplate.query(
                "SELECT id, state, city FROM " + tableName + " WHERE district IS NULL",
                (rs, rowNum) -> new BackfillRow(rs.getLong("id"), rs.getString("state"), rs.getString("city")));

        int mapped = 0;

        for (BackfillRow row : rows) {

            NameMatch match = indianLocationService.matchByNames(row.state(), row.city());

            if (match.mapped()) {

                int updated = jdbcTemplate.update(
                        "UPDATE " + tableName
                                + " SET district = ?, ulb_code = ? WHERE id = ? AND district IS NULL",
                        match.location().district(),
                        match.location().ulbCode(),
                        row.id());

                if (updated > 0) {
                    mapped++;
                }
                continue;
            }

            String auditKey = tableName + ":" + row.id();
            if (!alreadyAudited.add(auditKey)) {
                continue; // Already on the worklist from an earlier run: never duplicate
            }

            jdbcTemplate.update(
                    "INSERT INTO location_backfill_audit (table_name, row_id, city, state_value, reason) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    tableName, row.id(), row.city(), row.state(), match.reason());
        }

        return mapped;
    }

    /** Keys (table:id) already present in the audit table, so re-runs are idempotent. */
    private Set<String> existingAuditKeys() {

        Set<String> keys = new HashSet<>();

        try {
            jdbcTemplate.query(
                    "SELECT table_name, row_id FROM location_backfill_audit",
                    rs -> {
                        keys.add(rs.getString("table_name") + ":" + rs.getLong("row_id"));
                    });
        } catch (Exception ex) {
            log.warn("Could not read existing audit rows ({}); duplicates are possible on this run.",
                    ex.getMessage());
        }

        return keys;
    }

    /**
     * Verifies the additive DDL has been applied: the five new columns and
     * the audit table must exist before a single row is written.
     */
    private boolean requiredColumnsExist() {

        String[][] required = {
                {"users", "district"},
                {"users", "ulb_code"},
                {"garbage_reports", "district"},
                {"garbage_reports", "ulb_code"},
                {"municipal_corporations", "ulb_code"}
        };

        try {
            for (String[] column : required) {

                boolean present = !jdbcTemplate.queryForList(
                        "SELECT 1 FROM information_schema.columns "
                                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
                        String.class, column[0], column[1]).isEmpty();

                if (!present) {
                    log.error("Required column {}.{} is missing.", column[0], column[1]);
                    return false;
                }
            }

            boolean auditTable = !jdbcTemplate.queryForList(
                    "SELECT 1 FROM information_schema.tables "
                            + "WHERE table_schema = 'public' AND table_name = 'location_backfill_audit'",
                    String.class).isEmpty();

            if (!auditTable) {
                log.error("The location_backfill_audit table is missing.");
                return false;
            }

            return true;
        } catch (Exception ex) {
            log.error("Could not verify the location columns: {}", ex.getMessage());
            return false;
        }
    }

    /** Only PostgreSQL keeps legacy rows to backfill; anything else is left alone. */
    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());

            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception ex) {
            log.warn("Could not read the database product name, skipping location backfill: {}",
                    ex.getMessage());
            return false;
        }
    }

    /**
     * One row awaiting a mapping decision. Package-visible so the runner's
     * unit tests can feed rows through the mocked JDBC layer.
     */
    record BackfillRow(long id, String state, String city) {
    }
}
