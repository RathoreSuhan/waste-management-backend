package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.GarbageReport;

/**
 * Handles deletion of a GarbageReport together with every dependent resource.
 */
public interface ReportDeletionService {

    /**
     * Deletes one garbage report and all dependent data.
     */
    void deleteReport(GarbageReport report);

}