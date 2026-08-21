package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupActivityLogRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Optional work diary kept by the authorised cleaner while a site is IN_PROGRESS.
 *
 * Nothing in the cleanup lifecycle depends on these rows: a short one-day
 * cleanup can go from START CLEANUP straight to the proof upload without ever
 * writing an entry. Multi-day drives simply add one entry per visit, which is
 * what makes the log naturally multi-day.
 */
public interface CleanupActivityLogService {

    /*
     * Adds one entry to the diary.
     * Image and coordinates are optional; only the assigned cleaner of an
     * IN_PROGRESS assignment may write.
     */
    CleanupActivityLogResponse addActivityLog(Long assignmentId,
                                              CleanupActivityLogRequest request,
                                              MultipartFile image);

    // Full diary for one assignment, oldest entry first (reads like a timeline)
    List<CleanupActivityLogResponse> getActivityLogs(Long assignmentId);

    // Cleaner corrects a mistake; only their own entry and only while work continues
    void deleteActivityLog(Long activityLogId);
}