package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CleanupActivityLogRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.service.CleanupActivityLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Optional cleanup work diary of the authorized cleaner.
 *
 * Locked to ROLE_CLEANER in SecurityConfig; ownership of the assignment is
 * re-checked inside the service, so a cleaner can never read or write another
 * cleaner's diary.
 */
@RestController
@RequestMapping("/api/cleanup-activity-logs")
@RequiredArgsConstructor
public class CleanupActivityLogController {

    // Cleanup Activity Log Service
    private final CleanupActivityLogService cleanupActivityLogService;

    /**
     * Adds one entry while the cleanup is IN_PROGRESS.
     *
     * Multipart because the photo is optional: the same endpoint serves a
     * text-only note and a note with evidence.
     */
    @PostMapping(value = "/assignment/{assignmentId}", consumes = "multipart/form-data")
    public ResponseEntity<CleanupActivityLogResponse> addActivityLog(
            @PathVariable Long assignmentId,

            // Description plus optional timestamp and coordinates
            @Valid @ModelAttribute CleanupActivityLogRequest request,

            // Optional evidence photo for this entry
            @RequestParam(value = "image", required = false) MultipartFile image
    ) {

        CleanupActivityLogResponse response =
                cleanupActivityLogService.addActivityLog(assignmentId, request, image);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Full diary of one assignment, oldest entry first.
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<CleanupActivityLogResponse>> getActivityLogs(@PathVariable Long assignmentId) {

        List<CleanupActivityLogResponse> response = cleanupActivityLogService.getActivityLogs(assignmentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Removes one of the cleaner's own entries (typo or wrong photo).
     */
    @DeleteMapping("/{activityLogId}")
    public ResponseEntity<SuccessResponse> deleteActivityLog(@PathVariable Long activityLogId) {

        cleanupActivityLogService.deleteActivityLog(activityLogId);

        SuccessResponse response = SuccessResponse.builder()
                .message("Activity entry deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }
}