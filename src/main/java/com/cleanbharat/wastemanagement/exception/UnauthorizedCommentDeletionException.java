package com.cleanbharat.wastemanagement.exception;

public class UnauthorizedCommentDeletionException extends RuntimeException {
    public UnauthorizedCommentDeletionException(String message) {
        super(message);
    }
}