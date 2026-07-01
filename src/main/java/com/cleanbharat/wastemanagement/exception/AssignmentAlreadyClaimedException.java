package com.cleanbharat.wastemanagement.exception;

public class AssignmentAlreadyClaimedException extends RuntimeException {
    public AssignmentAlreadyClaimedException(String message) {
        super(message);
    }
}