package com.cleanbharat.wastemanagement.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.cleanbharat.wastemanagement.dto.ai.DuplicateReportResponse;
import com.cleanbharat.wastemanagement.dto.ai.ImageValidationErrorResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice // global handler
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidRegistrationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRegistrationException(InvalidRegistrationException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailAlreadyExistsException(EmailAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.CONFLICT.value());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(InvalidCredentialsException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.UNAUTHORIZED.value());
        return new ResponseEntity<>(error, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(InvalidPasswordChangeException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPasswordChangeException(InvalidPasswordChangeException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedRegistrationException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedRegistrationException(UnauthorizedRegistrationException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.NOT_FOUND.value());
        return new ResponseEntity<>(error, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidReportCreationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidReportCreationException(InvalidReportCreationException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles AI report validation failures.
     *
     * The rejection reason and the AI's own remarks are passed through so the
     * citizen can be told why the photograph was refused and what to do about
     * it, instead of only that it failed.
     */
    @ExceptionHandler(InvalidReportImageException.class)
    public ResponseEntity<ImageValidationErrorResponse> handleInvalidReportImageException(InvalidReportImageException ex) {

        ImageValidationErrorResponse error =
                ImageValidationErrorResponse.builder()

                        // Guidance for the citizen
                        .message(ex.getMessage())

                        // HTTP status
                        .status(HttpStatus.BAD_REQUEST.value())

                        // Why the image was rejected (null for non-AI failures)
                        .reason(ex.getReason())

                        // What the AI reported seeing
                        .aiRemarks(ex.getAiRemarks())

                        // How sure the AI was
                        .confidence(ex.getConfidence())

                        .build();

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles duplicate garbage report submissions.
     */
    @ExceptionHandler(DuplicateReportException.class)
    public ResponseEntity<DuplicateReportResponse> handleDuplicateReportException(DuplicateReportException ex) {

        DuplicateReportResponse response =
                DuplicateReportResponse.builder()

                        // Duplicate detected
                        .duplicate(true)

                        // Friendly message
                        .message(ex.getMessage())

                        // HTTP status
                        .status(HttpStatus.CONFLICT.value())

                        // Existing nearby report
                        .existingReportId(ex.getExistingReportId())

                        // Distance from submitted location
                        .distanceMeters(Double.valueOf(ex.getDistanceMeters()))

                        // AI detected garbage category
                        .garbageCategory(ex.getGarbageCategory())

                        .build();

        return new ResponseEntity<>(
                response,
                HttpStatus.CONFLICT
        );
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

    @ExceptionHandler(AssignmentAlreadyClaimedException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentAlreadyClaimedException(AssignmentAlreadyClaimedException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.CONFLICT.value());
        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidAssignmentStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAssignmentStateException(InvalidAssignmentStateException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(UnauthorizedAssignmentAccessException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorizedAssignmentAccessException(UnauthorizedAssignmentAccessException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.FORBIDDEN.value());
        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AssignmentAlreadyCompletedException.class)
    public ResponseEntity<ErrorResponse> handleAssignmentAlreadyCompletedException(AssignmentAlreadyCompletedException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(CleanupNotStartedException.class)
    public ResponseEntity<ErrorResponse> handleCleanupNotStartedException(CleanupNotStartedException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles cleanup proof uploaded from outside the reported site.
     *
     * The message already names the measured distance and the permitted
     * radius, so it is passed through unchanged for the cleaner to read.
     */
    @ExceptionHandler(CleanerTooFarFromSiteException.class)
    public ResponseEntity<ErrorResponse> handleCleanerTooFarFromSiteException(CleanerTooFarFromSiteException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles AI service failures.
     */
    @ExceptionHandler(AIServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAIServiceUnavailableException(AIServiceUnavailableException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.SERVICE_UNAVAILABLE.value());
        return new ResponseEntity<>(error, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(UserDeletionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleUserDeletionNotAllowed(UserDeletionNotAllowedException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RolePromotionNotAllowedException.class)
    public ResponseEntity<ErrorResponse> handleRolePromotionNotAllowed(RolePromotionNotAllowedException ex) {
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex){

        ErrorResponse error = new ErrorResponse(
                "Invalid value '" + ex.getValue()
                        + "' for parameter '" + ex.getName() + "'.",
                HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    // Handles validation errors when @Valid annotation fails on request body
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        // Get the first field error message for user-friendly feedback
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");

        ErrorResponse error = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles validation failures on form-data payloads bound with
     * @Valid @ModelAttribute, such as the create-report multipart request.
     *
     * These raise BindException rather than MethodArgumentNotValidException,
     * so without this handler they would fall through to the catch-all below
     * and be reported as a 500.
     *
     * The constraint messages already name their field ("City is required"),
     * so the raw field name is not prefixed here.
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBindException(BindException ex) {

        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("Validation failed. Please check the submitted details.");

        ErrorResponse error = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles a missing file part, e.g. a report submitted without its photo.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestPart(MissingServletRequestPartException ex) {

        ErrorResponse error = new ErrorResponse(
                "Required file '" + ex.getRequestPartName() + "' is missing.",
                HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles a missing request parameter.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException ex) {

        ErrorResponse error = new ErrorResponse(
                "Required parameter '" + ex.getParameterName() + "' is missing.",
                HttpStatus.BAD_REQUEST.value()
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handles uploads larger than the configured multipart limit.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {

        ErrorResponse error = new ErrorResponse(
                "The uploaded image is too large. Please upload an image under 10MB.",
                HttpStatus.PAYLOAD_TOO_LARGE.value()
        );

        return new ResponseEntity<>(error, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    /**
     * Handles JSON deserialization errors (e.g., invalid enum values, malformed JSON).
     * Provides user-friendly error messages instead of technical Jackson errors.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Invalid request format. Please check your input.";
        
        // Provide specific hint for enum deserialization errors
        if (ex.getCause() != null && ex.getCause().getMessage().contains("CleanerType")) {
            message = "Invalid cleaner type selected. Please choose from: INDIVIDUAL, NGO, PRIVATE, or MUNICIPAL.";
        }
        
        ErrorResponse error = new ErrorResponse(message, HttpStatus.BAD_REQUEST.value());
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex){
        ErrorResponse error = new ErrorResponse(ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR.value());
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}