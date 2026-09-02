package com.cleanbharat.wastemanagement.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Unit tests for the database-failure branch of the global handler.
 *
 * Why this class matters:
 *
 * Every database rejection used to answer HTTP 409 with "This could not be saved
 * because it conflicts with existing records" - the wording the citizen report
 * form uses for a duplicate report. When garbage_reports.description was still
 * varchar(255) while the API accepted 500 characters, a perfectly valid report
 * failed with SQLSTATE 22001 and the citizen was shown that duplicate wording,
 * with no existing report to link to and nothing they could act on.
 *
 * So the two behaviours pinned here are the split itself - a value that does not
 * fit its column is a bad request, a real constraint violation stays a conflict -
 * and the promise that no raw SQL ever leaves the server, because the same
 * handler exists to keep the JDBC dump out of the user's error banner.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** The rejection text as the PostgreSQL driver reports it, statement included. */
    private static final String RAW_JDBC_MESSAGE =
            "ERROR: value too long for type character varying(255); "
                    + "SQL [insert into garbage_reports (address,city,description) values (?,?,?)]";

    private static final String CONFLICT_MESSAGE =
            "This could not be saved because it conflicts with existing records. "
                    + "Please try again, and contact support if it keeps happening.";

    /** Builds the exception Spring hands the handler for a given SQLSTATE. */
    private static DataIntegrityViolationException wrap(String sqlState, String message) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException(message, sqlState));
    }

    // ==========================================================
    // The split on SQLSTATE
    // ==========================================================

    /**
     * SQLSTATE 22001 is the report that failed in production: a 256-500 character
     * description against a varchar(255) column.
     */
    @Test
    void overLongValueIsAnsweredAsABadRequestRatherThanAConflict() {

        ResponseEntity<ErrorResponse> response =
                handler.handleDataIntegrityViolation(wrap("22001", RAW_JDBC_MESSAGE));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getBody().getStatus());

        // The message has to tell the citizen what to actually do about it
        assertEquals(
                "Some of the text you entered is longer than this server can store. "
                        + "Please shorten it and try again.",
                response.getBody().getMessage());
    }

    /**
     * 23505 is a genuine unique violation. It must keep answering 409 with the
     * unchanged wording, because getDuplicateReportDetails() on the frontend
     * treats 409 as the duplicate-report status.
     */
    @Test
    void uniqueViolationKeepsTheConflictResponse() {

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                wrap("23505", "duplicate key value violates unique constraint \"uk_votes_user\""));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(HttpStatus.CONFLICT.value(), response.getBody().getStatus());
        assertEquals(CONFLICT_MESSAGE, response.getBody().getMessage());
    }

    /** 23502 is a NOT NULL violation, also a conflict as far as the caller is told. */
    @Test
    void notNullViolationKeepsTheConflictResponse() {

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(
                wrap("23502", "null value in column \"city\" violates not-null constraint"));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(CONFLICT_MESSAGE, response.getBody().getMessage());
    }

    // ==========================================================
    // Finding the SQLSTATE, and what happens when there is none
    // ==========================================================

    /**
     * Hibernate wraps the driver exception before Spring wraps Hibernate, so the
     * SQLSTATE is never on the immediate cause in a real stack.
     */
    @Test
    void sqlStateIsFoundThroughANestedWrapper() {

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException(
                        "org.hibernate.exception.DataException",
                        new SQLException(RAW_JDBC_MESSAGE, "22001")));

        assertEquals(
                HttpStatus.BAD_REQUEST,
                handler.handleDataIntegrityViolation(ex).getStatusCode());
    }

    /**
     * Some failures are raised by Hibernate itself and never reach the driver, so
     * there is no SQLSTATE to read. Those keep the previous behaviour.
     */
    @Test
    void failureWithoutAnSqlStateStaysAConflict() {

        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "not-null property references a null or transient value");

        ResponseEntity<ErrorResponse> response = handler.handleDataIntegrityViolation(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(CONFLICT_MESSAGE, response.getBody().getMessage());
    }

    // ==========================================================
    // Nothing internal reaches the browser
    // ==========================================================

    /** Neither branch may leak the statement, the table or the column type. */
    @Test
    void noRawSqlEverReachesTheCaller() {

        for (String sqlState : new String[]{"22001", "23505"}) {

            String message = handler.handleDataIntegrityViolation(
                    wrap(sqlState, RAW_JDBC_MESSAGE)).getBody().getMessage();

            assertFalse(message.contains("insert into"), "leaked the statement for " + sqlState);
            assertFalse(message.contains("garbage_reports"), "leaked the table for " + sqlState);
            assertFalse(message.contains("character varying"), "leaked the type for " + sqlState);
        }
    }
}
