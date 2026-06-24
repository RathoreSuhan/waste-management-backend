package com.cleanbharat.wastemanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // global handler
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidVoteException.class)
    public ResponseEntity<ErrorResponse> handleInvalidVoteException(InvalidVoteException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CommentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCommentNotFoundException(CommentNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UnauthorizedCommentDeletionException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedCommentDeletionException(UnauthorizedCommentDeletionException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex){
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}