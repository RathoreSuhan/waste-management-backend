package com.cleanbharat.wastemanagement.exception;

import com.cleanbharat.wastemanagement.enums.ImageRejectionReason;
import lombok.Getter;

/**
 * Thrown when the uploaded report image fails AI validation.
 *
 * Carries the structured rejection details so the API can tell the citizen
 * WHY the photograph was refused instead of only that it was refused.
 * Everything beyond the message is optional, which keeps the plain
 * single-argument constructor usable for non-AI failures such as an
 * unsupported file format.
 */
@Getter
public class InvalidReportImageException extends RuntimeException {

    // Machine readable rejection reason (null for non-AI failures)
    private final ImageRejectionReason reason;

    // Raw explanation returned by the AI, kept verbatim
    private final String aiRemarks;

    // AI confidence in its own assessment (0.0 - 1.0)
    private final Double confidence;

    /**
     * Failure with no AI verdict behind it, e.g. an unreadable upload.
     */
    public InvalidReportImageException(String message) {
        super(message);
        this.reason = null;
        this.aiRemarks = null;
        this.confidence = null;
    }

    /**
     * Failure backed by an AI verdict.
     *
     * @param message   guidance shown to the citizen
     * @param reason    why the image was rejected
     * @param aiRemarks the AI's own observation about the image
     * @param confidence how sure the AI was
     */
    public InvalidReportImageException(
            String message,
            ImageRejectionReason reason,
            String aiRemarks,
            Double confidence
    ) {
        super(message);
        this.reason = reason;
        this.aiRemarks = aiRemarks;
        this.confidence = confidence;
    }
}
