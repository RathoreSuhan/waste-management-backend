package com.cleanbharat.wastemanagement.dto;

import lombok.*;

/**
 * Response returned after cleanup image upload
 * and AI verification.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupValidationResponse {

    // Whether AI verified the cleanup
    private Boolean aiVerified;

    // AI confidence score (0.0 - 1.0)
    private Double confidence;

    // AI explanation
    private String remarks;

    // Current assignment status
    private String assignmentStatus;

    // Current garbage report status
    private String reportStatus;

    // Friendly message for frontend
    private String message;
}