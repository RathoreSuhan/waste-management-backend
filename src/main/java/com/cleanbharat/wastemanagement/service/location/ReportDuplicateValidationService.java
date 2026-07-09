package com.cleanbharat.wastemanagement.service.location;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;

/**
 * Validates that a newly submitted report is not a duplicate of an existing report.
 */
public interface ReportDuplicateValidationService {

    /**
     * Ensures that no nearby recent report already exists.
     */
    void validateNoDuplicateReport(CreateReportRequest request);

}