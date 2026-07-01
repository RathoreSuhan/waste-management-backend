package com.cleanbharat.wastemanagement.exception;

public class UnauthorizedRegistrationException extends RuntimeException {
    public UnauthorizedRegistrationException(String message) {
        super(message);
    }
}