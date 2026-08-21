package com.cleanbharat.wastemanagement.exception;

/**
 * Raised when a proposal action is not allowed in its current state, e.g.
 * editing an already approved proposal or proposing for a site that is
 * no longer open for offers.
 */
public class InvalidProposalStateException extends RuntimeException {

    public InvalidProposalStateException(String message) {
        super(message);
    }
}
