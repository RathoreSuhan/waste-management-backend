package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when a user cannot be deleted because of business rules.
 */
public class UserDeletionNotAllowedException extends RuntimeException {

    public UserDeletionNotAllowedException(String message) {
        super(message);
    }
}