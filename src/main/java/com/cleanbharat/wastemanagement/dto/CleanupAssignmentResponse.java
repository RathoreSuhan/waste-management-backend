package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupAssignmentResponse {

    // Assignment ID
    private Long assignmentId;

    // Original garbage report ID
    private Long reportId;

    // Garbage report title
    private String reportTitle;

    // Garbage report description
    private String reportDescription;

    // Before-cleanup image uploaded by citizen
    private String beforeImageUrl;

    // After-cleanup image uploaded by cleaner
    private String afterImageUrl;

    // Garbage report address
    private String address;

    // City where cleanup is required
    private String city;

    // Current assignment status
    private String assignmentStatus;

    // Current garbage report status
    private String reportStatus;

    // Name of assigned cleaner
    private String cleanerName;

    // Municipal Corporation responsible
    private String municipalCorporation;

    // AI verification result
    private Boolean aiVerified;

    // AI confidence score
    private Double aiConfidence;

    // AI remarks
    private String aiRemarks;

    // Assignment claim time
    private LocalDateTime claimedAt;

    // Cleanup start time
    private LocalDateTime startedAt;

    // Cleanup completion time
    private LocalDateTime completedAt;

    // Garbage report creation time
    private LocalDateTime reportCreatedAt;
}