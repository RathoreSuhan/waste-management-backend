package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when a new garbage report
 * is detected as a duplicate of an
 * existing nearby report.
 */
public class DuplicateReportException extends RuntimeException {

    public DuplicateReportException(String message) {
        super(message);
    }

}