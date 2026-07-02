package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when AI verification service
 * is temporarily unavailable.
 */
public class AIServiceUnavailableException extends RuntimeException {

    public AIServiceUnavailableException(String message) {
        super(message);
    }

    public AIServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}