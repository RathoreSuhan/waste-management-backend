package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

/*
 * One activity entry as returned to the cleaner UI (and later the municipal
 * dashboard, which is out of scope for this phase).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupActivityLogResponse {

    private Long activityLogId;

    private Long assignmentId;

    private String description;

    // When the work happened - the timeline is ordered on this field
    private LocalDateTime activityAt;

    // Optional progress photograph
    private String imageUrl;

    private Double latitude;

    private Double longitude;

    // Distance from the reported site, shown for information only
    private Double distanceMeters;

    // Author, so a re-awarded assignment still reads correctly
    private String cleanerName;

    private LocalDateTime createdAt;
}