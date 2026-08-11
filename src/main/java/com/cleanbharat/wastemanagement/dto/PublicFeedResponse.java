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

    // Total number of views
    private Long viewCount;

    // Total likes received
    private Long likeCount;

    // Total shares
    private Long shareCount;

    /*
      Whether the person reading this has already appreciated the cleanup.

      Sent so the heart can be shown filled on a fresh page load, on any
      device. The page previously remembered likes in browser storage,
      which meant the same account saw a different state on a different
      browser, and one browser applied its memory to every account that
      used it.

      False for visitors who are not signed in: a like belongs to an
      account, so an anonymous reader cannot have given one.
     */
    private Boolean likedByMe;
}


