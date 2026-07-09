package com.cleanbharat.wastemanagement.dto.ai;

import lombok.*;

/**
 * Response returned when a duplicate garbage report is detected.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DuplicateReportResponse {

    // Indicates duplicate report found
    private Boolean duplicate;

    // Friendly message for frontend
    private String message;

    // HTTP status code
    private Integer status;

    // Existing nearby report
    private Long existingReportId;

    // Distance from newly submitted location
    private Double distanceMeters;

    // AI detected garbage category
    private String garbageCategory;

}