package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.service.CleanupAssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/cleanup-assignments")
@RequiredArgsConstructor
public class CleanupAssignmentController {

    // Cleanup Assignment Service
    private final CleanupAssignmentService cleanupAssignmentService;

    /**
     * Cleaner claims a pending assignment.
     */
    @PostMapping("/{assignmentId}/claim")
    public ResponseEntity<SuccessResponse> claimAssignment(@PathVariable Long assignmentId) {

        // Delegate business logic to service
        cleanupAssignmentService.claimAssignment(assignmentId);

        // Build success response
        SuccessResponse response = SuccessResponse.builder()
                .message("Assignment claimed successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Cleaner started the claimed assignment.
     */
    @PostMapping("/{assignmentId}/start")
    public ResponseEntity<SuccessResponse> startCleanup(@PathVariable Long assignmentId) {

        // Delegate business logic
        cleanupAssignmentService.startCleanup(assignmentId);

        // Success response
        SuccessResponse response = SuccessResponse.builder()
                .message("Cleanup started successfully.")
                .timestamp(LocalDateTime.now())
                .build();

        return ResponseEntity.ok(response);
    }

    //cleaner upload cleaned image for the claimed assignment for AI verification
    @PostMapping(value = "/{assignmentId}/upload-image", consumes = "multipart/form-data")
    public ResponseEntity<CleanupValidationResponse> uploadCleanupImage(
            @PathVariable Long assignmentId,
            @RequestParam("image") MultipartFile image,

            /*
             * Position captured by the cleaner's device.
             *
             * Optional at binding level only so a request without it produces
             * the guidance message from the service instead of a generic
             * "required parameter is missing" error.
             */
            @RequestParam(value = "latitude", required = false) Double latitude,
            @RequestParam(value = "longitude", required = false) Double longitude
    ) {

        // Upload image and validate using AI
        CleanupValidationResponse response =
                cleanupAssignmentService.uploadCleanupImage(
                        assignmentId,
                        image,
                        latitude,   // Verified against the report location
                        longitude
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Returns every assignment of the logged-in cleaner.
     */
    @GetMapping("/my-tasks")
    public ResponseEntity<List<CleanupAssignmentResponse>> getMyTasks() {

        // Fetch all assignments of logged-in cleaner
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getMyTasks();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns all pending assignments that are available for claiming.
     */
    @GetMapping("/pending")
    public ResponseEntity<List<CleanupAssignmentResponse>> getPendingAssignments() {

        // Fetch all unclaimed pending assignments
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getPendingAssignments();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns claimed assignments of the logged-in cleaner.
     */
    @GetMapping("/claimed")
    public ResponseEntity<List<CleanupAssignmentResponse>> getClaimedAssignments() {

        // Fetch claimed assignments
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getClaimedAssignments();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns assignments currently being cleaned.
     */
    @GetMapping("/in-progress")
    public ResponseEntity<List<CleanupAssignmentResponse>> getInProgressAssignments() {

        // Fetch assignments under cleanup
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getInProgressAssignments();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns successfully completed assignments.
     */
    @GetMapping("/completed")
    public ResponseEntity<List<CleanupAssignmentResponse>> getCompletedAssignments() {

        // Fetch completed assignments
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getCompletedAssignments();

        return ResponseEntity.ok(response);
    }

    /**
     * Returns nearby assignments.
     * Current implementation:
     * Returns all pending assignments.

     * Future:
     * Filter using Google Maps distance.
     */
    @GetMapping("/nearby")
    public ResponseEntity<List<CleanupAssignmentResponse>> getNearbyAssignments() {

        // Fetch nearby (currently all pending) assignments
        List<CleanupAssignmentResponse> response = cleanupAssignmentService.getNearbyAssignments();

        return ResponseEntity.ok(response);
    }
}