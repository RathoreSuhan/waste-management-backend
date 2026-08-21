package com.cleanbharat.wastemanagement.exception;

/**
 * Raised when a proposal id does not exist, or does not belong to the caller.
 */
public class ProposalNotFoundException extends RuntimeException {

    public ProposalNotFoundException(String message) {
        super(message);
    }
}
