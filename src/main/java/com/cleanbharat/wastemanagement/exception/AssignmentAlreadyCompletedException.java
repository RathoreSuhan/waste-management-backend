package com.cleanbharat.wastemanagement.exception;

/**
 * Thrown when a completed assignment
 * is modified again.
 */
public class AssignmentAlreadyCompletedException extends RuntimeException {
    public AssignmentAlreadyCompletedException(String message) {
        super(message);
    }
}