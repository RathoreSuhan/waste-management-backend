package com.cleanbharat.wastemanagement.exception;

/**
 * Raised when a cleaner already has a live proposal for the same assignment.
 * One offer per cleaner keeps the municipal review queue readable.
 */
public class DuplicateProposalException extends RuntimeException {

    public DuplicateProposalException(String message) {
        super(message);
    }
}
