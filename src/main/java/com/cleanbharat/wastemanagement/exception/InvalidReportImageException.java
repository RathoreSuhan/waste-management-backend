package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when the uploaded report image fails AI validation.
 */
public class InvalidReportImageException extends RuntimeException {
    public InvalidReportImageException(String message) {
        super(message);
    }
}