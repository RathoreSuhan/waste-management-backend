package com.cleanbharat.wastemanagement.exception;

import lombok.Getter;

/**
 * Thrown when a newly submitted garbage report
 * is detected as a duplicate of an existing
 * nearby report.
 */
@Getter
public class DuplicateReportException extends RuntimeException {

    // Existing nearby report ID
    private final Long existingReportId;

    // Distance from submitted location (meters)
    private final Integer distanceMeters;

    // AI detected garbage category
    private final String garbageCategory;

    public DuplicateReportException(
            String message,
            Long existingReportId,
            Integer distanceMeters,
            String garbageCategory) {

        super(message);

        this.existingReportId = existingReportId;
        this.distanceMeters = distanceMeters;
        this.garbageCategory = garbageCategory;
    }

}