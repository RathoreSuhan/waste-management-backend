package com.cleanbharat.wastemanagement.dto.ai;

import com.cleanbharat.wastemanagement.enums.ImageRejectionReason;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

/**
 * Error body returned when an uploaded report image fails AI validation.
 *
 * Keeps message and status identical to ErrorResponse, so existing clients
 * that only read the message keep working, and adds the structured rejection
 * details for clients that want to show tailored guidance.
 *
 * The AI fields are omitted when absent, which keeps non-AI failures such as
 * an unsupported file format looking exactly like a plain ErrorResponse.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageValidationErrorResponse {

    // Guidance shown to the citizen
    private String message;

    // HTTP status
    private int status;

    // Machine-readable rejection reason
    private ImageRejectionReason reason;

    // What the AI reported seeing in the image
    private String aiRemarks;

    // AI confidence (0.0 - 1.0)
    private Double confidence;
}
