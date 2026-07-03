package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicFeedResponse {

    // Garbage report ID
    private Long reportId;

    // Garbage report title
    private String reportTitle;

    // Garbage report description
    private String reportDescription;

    // Before-cleanup image uploaded by citizen
    private String beforeImageUrl;

    // After-cleanup image uploaded by cleaner
    private String afterImageUrl;

    // Cleanup location address
    private String address;

    // Nearby landmark
    private String landmark;

    // City where cleanup was performed
    private String city;

    // State where cleanup was performed
    private String state;

    // Name of the cleaner
    private String cleanerName;

    // Type/category of cleaner
    private String cleanerType;

    // Responsible Municipal Corporation
    private String municipalCorporationName;

    // Time when cleanup was completed
    private LocalDateTime cleanupCompletedTime;

    // Final garbage report status
    private String reportStatus;

    // AI verification result
    private Boolean aiVerified;

    // AI confidence percentage
    private Double aiConfidence;

    // AI verification remarks
    private String aiRemarks;
}