package com.cleanbharat.wastemanagement.exception;

/**
 * Raised when the bundled location directory is missing or unreadable.
 *
 * Distinct from InvalidRegistrationException because the caller did nothing
 * wrong: the right answer is to retry, not to correct the form. Answered as
 * 503 by GlobalExceptionHandler - in particular, never as an acceptance, so a
 * broken dataset can never turn registration back into free text.
 */
public class LocationDataUnavailableException extends RuntimeException {
    public LocationDataUnavailableException(String message) {
        super(message);
    }
}
