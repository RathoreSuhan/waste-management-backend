package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CleanupAssignmentService {

    // Creates default assignment after report creation
    void createDefaultAssignment(GarbageReport report);

    // Cleaner claims a pending assignment
    void claimAssignment(Long assignmentId);

    // Cleaner starts an already claimed assignment
    void startCleanup(Long assignmentId);

    // Upload after-cleanup image and validate using AI
    CleanupValidationResponse uploadCleanupImage(
            Long assignmentId,
            MultipartFile image
    );

    // Returns every assignment claimed by the logged-in cleaner
    List<CleanupAssignmentResponse> getMyTasks();

    // Returns all unclaimed assignments available to cleaners
    List<CleanupAssignmentResponse> getPendingAssignments();

    // Returns assignments claimed but not yet started
    List<CleanupAssignmentResponse> getClaimedAssignments();

    // Returns assignments currently being cleaned
    List<CleanupAssignmentResponse> getInProgressAssignments();

    // Returns successfully completed assignments
    List<CleanupAssignmentResponse> getCompletedAssignments();

    // Returns nearby assignments
    // (currently city-based, later Google Maps)
    List<CleanupAssignmentResponse> getNearbyAssignments();
}