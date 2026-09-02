package com.cleanbharat.wastemanagement.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the startup repair that widens columns ddl-auto=update left
 * behind.
 *
 * The runner issues DDL against a live database, so what matters is not only
 * that it widens the right column but that it stays quiet in every other
 * situation. Each test below pins one of those situations, using a mocked
 * JdbcTemplate so no database is needed:
 *
 *   - a database that is not PostgreSQL is never touched at all
 *   - a column narrower than its entity is widened, once
 *   - a column that is already wide enough is left alone, which is what makes
 *     the runner safe to keep on every boot
 *   - a column that does not exist yet is skipped
 *   - a column already changed to unbounded text is never narrowed back to a
 *     varchar, which would be the one genuinely destructive outcome here
 */
@ExtendWith(MockitoExtension.class)
class ColumnWidthInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private ColumnWidthInitializer columnWidthInitializer;

    private static final String WIDEN_DESCRIPTION =
            "ALTER TABLE garbage_reports ALTER COLUMN description TYPE varchar(500)";

    private static final String WIDEN_COMMENT_MESSAGE =
            "ALTER TABLE comments ALTER COLUMN message TYPE varchar(1000)";

    // ==========================================================
    // Helpers
    // ==========================================================

    /** Stubs the product name the runner reads before doing anything. */
    @SuppressWarnings("unchecked")
    private void givenDatabaseProduct(String product) {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn(product);
    }

    /** Stubs the live width information_schema reports for one column. */
    private void givenLiveWidth(String table, String column, Integer width) {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(table), eq(column)))
                .thenReturn(width == null
                        ? Collections.singletonList((Integer) null) // unbounded text column
                        : List.of(width));
    }

    /** Stubs a column that information_schema does not know about. */
    private void givenColumnAbsent(String table, String column) {
        when(jdbcTemplate.queryForList(anyString(), eq(Integer.class), eq(table), eq(column)))
                .thenReturn(List.of());
    }

    private void assertNoDdlWasIssued() {
        verify(jdbcTemplate, never()).execute(anyString());
    }

    // ==========================================================
    // Tests
    // ==========================================================

    /** Other databases are created from today's entities, so there is nothing to repair. */
    @Test
    void noDdlIsIssuedOnANonPostgresDatabase() {

        givenDatabaseProduct("H2");

        columnWidthInitializer.run();

        assertNoDdlWasIssued();
        verify(jdbcTemplate, never()).queryForList(anyString(), eq(Integer.class), any(), any());
    }

    /**
     * The production case: description is still varchar(255) while the entity
     * declares 500, and comments.message is already correct. Only the narrow
     * column may be altered.
     */
    @Test
    void aNarrowColumnIsWidenedAndAWideEnoughOneIsLeftAlone() {

        givenDatabaseProduct("PostgreSQL");
        givenLiveWidth("garbage_reports", "description", 255);
        givenLiveWidth("comments", "message", 1000);

        columnWidthInitializer.run();

        verify(jdbcTemplate).execute(WIDEN_DESCRIPTION);
        verify(jdbcTemplate, never()).execute(WIDEN_COMMENT_MESSAGE);
    }

    /** Every boot after the repair must be a complete no-op. */
    @Test
    void nothingIsAlteredOnceBothColumnsAreWideEnough() {

        givenDatabaseProduct("PostgreSQL");
        givenLiveWidth("garbage_reports", "description", 500);
        givenLiveWidth("comments", "message", 1000);

        columnWidthInitializer.run();

        assertNoDdlWasIssued();
    }

    /** A fresh database has no such column yet; Hibernate creates it correctly. */
    @Test
    void anAbsentColumnIsSkipped() {

        givenDatabaseProduct("PostgreSQL");
        givenColumnAbsent("garbage_reports", "description");
        givenColumnAbsent("comments", "message");

        columnWidthInitializer.run();

        assertNoDdlWasIssued();
    }

    /**
     * character_maximum_length is NULL for an unbounded text column. Widening it
     * to a varchar would narrow it, so the runner has to leave it as it is.
     */
    @Test
    void anUnboundedTextColumnIsNeverNarrowedToAVarchar() {

        givenDatabaseProduct("PostgreSQL");
        givenLiveWidth("garbage_reports", "description", null);
        givenLiveWidth("comments", "message", null);

        columnWidthInitializer.run();

        assertNoDdlWasIssued();
    }

    /**
     * A failed ALTER must never stop the application: the report feature works
     * for short descriptions even while the column is still narrow.
     */
    @Test
    void aFailedAlterIsSwallowedSoStartupContinues() {

        givenDatabaseProduct("PostgreSQL");
        givenLiveWidth("garbage_reports", "description", 255);
        givenLiveWidth("comments", "message", 255);

        // First ALTER is refused, e.g. the deploy user does not own the table
        doThrow(new DataAccessResourceFailureException("permission denied"))
                .when(jdbcTemplate).execute(WIDEN_DESCRIPTION);

        columnWidthInitializer.run();

        // The second column is still repaired, so one bad grant cannot hide the other fix
        verify(jdbcTemplate).execute(WIDEN_COMMENT_MESSAGE);
    }
}
