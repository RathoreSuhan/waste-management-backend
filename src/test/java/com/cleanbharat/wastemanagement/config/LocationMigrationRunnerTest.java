package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the operator-controlled location backfill (plan rule v6-2).
 *
 * The runner is exercised directly - the property gate that keeps it out of
 * ordinary startup is an annotation on the bean, so these tests never need it.
 * The JDBC layer is mocked; the directory is the real generated resource, so
 * the mapped expectations below are the workbook's own values:
 * Punjab 3 / S.A.S Nagar 608 / Mohali ULB 248325, Bihar 10 / Gaya 196 / 249680.
 */
class LocationMigrationRunnerTest {

    private static IndianLocationService locationService;

    private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void loadDirectory() {
        locationService = new IndianLocationService(new ObjectMapper());
        locationService.load();
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
    }

    private LocationMigrationRunner runner() {
        return new LocationMigrationRunner(jdbcTemplate, locationService);
    }

    /** Stubs the PostgreSQL probe and the schema verification to the happy path. */
    private void stubHappySchema() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class), any(), any()))
                .thenReturn(List.of("1"));
        when(jdbcTemplate.queryForList(contains("information_schema.tables"), eq(String.class)))
                .thenReturn(List.of("1"));
    }

    private void stubTableRows(String sqlFragment, LocationMigrationRunner.BackfillRow... rows) {
        when(jdbcTemplate.query(contains(sqlFragment), any(RowMapper.class)))
                .thenReturn(List.of(rows));
    }

    private void stubAuditKeys(String... auditedTableIds) {
        // query(String, RowCallbackHandler) returns void, so the stub is set with doAnswer
        org.mockito.Mockito.doAnswer(invocation -> {
            RowCallbackHandler handler = invocation.getArgument(1);
            for (String key : auditedTableIds) {
                ResultSet rs = mock(ResultSet.class);
                int split = key.indexOf(':');
                when(rs.getString("table_name")).thenReturn(key.substring(0, split));
                when(rs.getLong("row_id")).thenReturn(Long.parseLong(key.substring(split + 1)));
                handler.processRow(rs);
            }
            return null;
        }).when(jdbcTemplate).query(contains("FROM location_backfill_audit"), any(RowCallbackHandler.class));
    }

    @Test
    void aNonPostgresDatabaseIsLeftAlone() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn("H2");

        runner().run();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aMissingColumnAbortsBeforeAnyWrite() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        // users.district exists, users.ulb_code does not: the verification loop must abort
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class), any(), any()))
                .thenReturn(List.of("1"))
                .thenReturn(List.of());

        runner().run();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void aMissingAuditTableAbortsBeforeAnyWrite() {
        when(jdbcTemplate.execute(any(ConnectionCallback.class))).thenReturn("PostgreSQL");
        when(jdbcTemplate.queryForList(contains("information_schema.columns"), eq(String.class), any(), any()))
                .thenReturn(List.of("1"));
        when(jdbcTemplate.queryForList(contains("information_schema.tables"), eq(String.class)))
                .thenReturn(List.of());

        runner().run();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void recognisedCitiesAreMappedWithinTheirOwnState() {
        stubHappySchema();
        stubAuditKeys();
        stubTableRows("FROM users",
                new LocationMigrationRunner.BackfillRow(11L, "Punjab", "S.a.s.nagar - Mohali"));
        stubTableRows("FROM garbage_reports",
                new LocationMigrationRunner.BackfillRow(21L, "Bihar", "Gaya"));

        runner().run();

        // Punjab row: mapped to the workbook's district + ULB code
        verify(jdbcTemplate).update(contains("UPDATE users"),
                eq("S.a.s Nagar"), eq("248325"), eq(11L));
        // Bihar row: mapped to its district + ULB code
        verify(jdbcTemplate).update(contains("UPDATE garbage_reports"),
                eq("Gaya"), eq("249680"), eq(21L));
        // Nothing was unmapped, so no audit rows were written
        verify(jdbcTemplate, never()).update(contains("location_backfill_audit"), any(), any(), any(), any(), any());
    }

    @Test
    void anUnrecognisedCityIsAuditedAndLeftUnmapped() {
        stubHappySchema();
        stubAuditKeys();
        stubTableRows("FROM users",
                new LocationMigrationRunner.BackfillRow(31L, "Bihar", "Patna City"));
        stubTableRows("FROM garbage_reports");

        runner().run();

        // Fail-closed: no mapping is guessed, so no UPDATE fires
        verify(jdbcTemplate, never()).update(contains("UPDATE users"), any(), any(), any());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(contains("location_backfill_audit"),
                eq("users"), eq(31L), eq("Patna City"), eq("Bihar"), reason.capture());
        assertTrue(reason.getValue().contains("no directory ULB named"), reason.getValue());
    }

    @Test
    void anAlreadyAuditedRowIsNotAuditedTwice() {
        stubHappySchema();
        stubAuditKeys("users:31");
        stubTableRows("FROM users",
                new LocationMigrationRunner.BackfillRow(31L, "Bihar", "Patna City"));
        stubTableRows("FROM garbage_reports");

        runner().run();

        verify(jdbcTemplate, never()).update(contains("location_backfill_audit"), any(), any(), any(), any(), any());
    }

    @Test
    void theUpdateGuardKeepsAlreadyMappedRowsUntouched() {
        stubHappySchema();
        stubAuditKeys();
        stubTableRows("FROM users",
                new LocationMigrationRunner.BackfillRow(11L, "Punjab", "S.a.s.nagar - Mohali"));
        stubTableRows("FROM garbage_reports");

        runner().run();

        // The row was selected as district IS NULL, and the UPDATE repeats that
        // guard, so a mapping written by a concurrent pass can never be overwritten
        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(updateSql.capture(), eq("S.a.s Nagar"), eq("248325"), eq(11L));
        assertTrue(updateSql.getValue().contains("AND district IS NULL"), updateSql.getValue());
    }

    @Test
    void municipalCorporationsIsNeverWritten() {
        stubHappySchema();
        stubAuditKeys();
        stubTableRows("FROM users",
                new LocationMigrationRunner.BackfillRow(11L, "Punjab", "S.a.s.nagar - Mohali"));
        stubTableRows("FROM garbage_reports");

        runner().run();

        // Rule v6-2: the corporation table has no state column, so the runner
        // never maps it - that is the T3 preflight's job through verified state
        verify(jdbcTemplate, never()).update(contains("municipal_corporations"), any(Object[].class));
    }
}
