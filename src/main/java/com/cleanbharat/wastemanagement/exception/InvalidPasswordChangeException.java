package com.cleanbharat.wastemanagement.exception;

public class InvalidPasswordChangeException extends RuntimeException {

    public InvalidPasswordChangeException(String message) {
        super(message);
    }
}