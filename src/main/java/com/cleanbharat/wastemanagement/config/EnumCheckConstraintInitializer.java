package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Keeps the PostgreSQL enum CHECK constraints in step with the Java enums.
 *
 * Hibernate writes a CHECK constraint for every @Enumerated(EnumType.STRING)
 * column, listing only the values that existed when the table was first
 * created. With ddl-auto=update that list is never rewritten again: "update"
 * adds tables, columns and indexes, but leaves an existing CHECK untouched.
 *
 * So every new enum value added later is silently rejected by the live
 * database. That is exactly how submitting a cleanup proposal started failing
 * with:
 *
 *   ERROR: new row for relation "cleanup_assignments" violates check
 *   constraint "cleanup_assignments_status_check"   (SQLSTATE 23514)
 *
 * even though AssignmentStatus.PROPOSAL_SUBMITTED had been in the Java code all
 * along, and every unit test passed (the test profile builds a fresh schema
 * from today's enums on each run).
 *
 * This runner rebuilds each of those constraints from the enum class itself, so
 * the list can never drift again - adding a new enum value is enough, no manual
 * SQL and no migration file to remember. It also removes the numbered
 * duplicates Hibernate sometimes leaves behind (..._check1), because a single
 * stale duplicate is enough to keep rejecting the new values.
 *
 * Safe by design: it only ever replaces validation rules, never data; it is
 * idempotent, so running on every startup is a no-op once the lists match; it
 * skips any database that is not PostgreSQL (the H2 test schema is already
 * generated from the current enums); and a failure is logged as a warning
 * instead of preventing the application from starting.
 *
 * Set cleanbharat.db.repair-enum-constraints=false to switch it off.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(0) // Repair the constraints before any other startup data task writes rows
@ConditionalOnProperty(
        name = "cleanbharat.db.repair-enum-constraints",
        havingValue = "true",
        matchIfMissing = true) // Enabled unless it is explicitly turned off
public class EnumCheckConstraintInitializer implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    /** Enum names only, so nothing user supplied can ever reach the DDL below. */
    private static final Pattern SAFE_ENUM_NAME = Pattern.compile("[A-Z0-9_]+");

    /**
     * One entry per @Enumerated(EnumType.STRING) column in the schema.
     * The allowed values are read from the enum class, never hard-coded here.
     */
    private record EnumColumn(String table, String column, Class<? extends Enum<?>> enumType) {

        /** The name Hibernate gives the constraint, and the name in the error message. */
        String constraintName() {
            return table + "_" + column + "_check";
        }
    }

    private static final List<EnumColumn> ENUM_COLUMNS = List.of(
            new EnumColumn("cleanup_assignments", "status", AssignmentStatus.class), // the one that was failing
            new EnumColumn("cleanup_proposals", "status", ProposalStatus.class),
            new EnumColumn("cleanup_approvals", "stage", ApprovalStage.class),
            new EnumColumn("cleanup_approvals", "decision", ApprovalDecision.class),
            new EnumColumn("garbage_reports", "status", ReportStatus.class),
            new EnumColumn("users", "role", Role.class),
            new EnumColumn("users", "cleaner_type", CleanerType.class)
    );

    @Override
    public void run(String... args) {

        if (!isPostgres()) {
            return; // H2 / other databases build their schema fresh, nothing to repair
        }

        int repaired = 0;

        for (EnumColumn enumColumn : ENUM_COLUMNS) {

            // A column that is not there yet (fresh database, renamed field) is simply skipped
            if (!columnExists(enumColumn)) {
                continue;
            }

            try {
                dropNumberedDuplicates(enumColumn); // e.g. cleanup_assignments_status_check1
                rebuildConstraint(enumColumn);      // replace the list with today's enum values
                repaired++;
            } catch (Exception ex) {
                // Never stop the application over a validation rule we could not refresh
                log.warn("Could not refresh CHECK constraint {}: {}",
                        enumColumn.constraintName(), ex.getMessage());
            }
        }

        log.info("Verified {} enum CHECK constraint(s) against the current Java enums.", repaired);
    }

    /** Only PostgreSQL keeps the stale constraints; everything else is left alone. */
    private boolean isPostgres() {
        try {
            String product = jdbcTemplate.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());

            return product != null && product.toLowerCase().contains("postgres");
        } catch (Exception ex) {
            log.warn("Could not read the database product name, skipping enum constraint repair: {}",
                    ex.getMessage());
            return false;
        }
    }

    /** Guards against altering a table or column that does not exist in this database. */
    private boolean columnExists(EnumColumn enumColumn) {
        try {
            Integer found = jdbcTemplate.queryForObject("""
                            SELECT count(*)
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = ?
                              AND column_name = ?
                            """,
                    Integer.class, enumColumn.table(), enumColumn.column());

            return found != null && found > 0;
        } catch (Exception ex) {
            log.warn("Could not inspect column {}.{}: {}",
                    enumColumn.table(), enumColumn.column(), ex.getMessage());
            return false;
        }
    }

    /**
     * Removes leftover copies such as cleanup_assignments_status_check1.
     *
     * Hibernate falls back to a numbered name when the plain one is already
     * taken, and that copy carries its own stale value list, so rebuilding only
     * the canonical constraint would not be enough.
     */
    private void dropNumberedDuplicates(EnumColumn enumColumn) {

        List<String> duplicates = jdbcTemplate.queryForList("""
                        SELECT con.conname
                        FROM pg_constraint con
                                 JOIN pg_class rel ON rel.oid = con.conrelid
                        WHERE con.contype = 'c'
                          AND con.connamespace = 'public'::regnamespace
                          AND rel.relname = ?
                          AND con.conname ~ ?
                        """,
                String.class,
                enumColumn.table(),
                "^" + enumColumn.constraintName() + "[0-9]+$"); // only the numbered clones

        for (String duplicate : duplicates) {
            jdbcTemplate.execute("ALTER TABLE " + enumColumn.table()
                    + " DROP CONSTRAINT IF EXISTS \"" + duplicate + "\"");

            log.info("Dropped duplicate CHECK constraint {} on {}.", duplicate, enumColumn.table());
        }
    }

    /** Drops the old rule and adds it back listing every value the enum defines today. */
    private void rebuildConstraint(EnumColumn enumColumn) {

        String allowedValues = Stream.of(enumColumn.enumType().getEnumConstants())
                .map(Enum::name)
                .filter(name -> SAFE_ENUM_NAME.matcher(name).matches()) // defensive, enums are constants
                .map(name -> "'" + name + "'")
                .collect(Collectors.joining(", "));

        if (allowedValues.isEmpty()) {
            return; // An empty enum would build a constraint nothing can satisfy
        }

        jdbcTemplate.execute("ALTER TABLE " + enumColumn.table()
                + " DROP CONSTRAINT IF EXISTS " + enumColumn.constraintName());

        jdbcTemplate.execute("ALTER TABLE " + enumColumn.table()
                + " ADD CONSTRAINT " + enumColumn.constraintName()
                + " CHECK (" + enumColumn.column() + " IN (" + allowedValues + "))");
    }
}