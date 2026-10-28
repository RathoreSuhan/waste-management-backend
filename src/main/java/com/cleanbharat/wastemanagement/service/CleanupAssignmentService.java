package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface CleanupAssignmentService {

    // Creates default assignment after report creation, resolving the
    // destination from the report's stored city (legacy free-text reports)
    void createDefaultAssignment(GarbageReport report);

    /**
     * Creates the default assignment with a destination that was already
     * resolved: code-backed reports find their Municipal Corporation by ULB
     * code before any expensive work runs, so the resolved office is handed
     * in rather than looked up again.
     *
     * @param preResolvedDestination the corporation found for the report's
     *                              ULB code; when null the destination falls
     *                              back to the report's stored city exactly
     *                              as {@link #createDefaultAssignment} does
     */
    void createDefaultAssignment(GarbageReport report, MunicipalCorporation preResolvedDestination);

    // claimAssignment() removed: cleaners now submit proposals for municipal approval

    /*
     * Authorized cleaner starts the approved cleanup.
     *
     * The captured position is start-of-work location evidence: it proves the
     * cleaner was at the site before work began and is verified against the
     * same 50 m radius used for the proof upload.
     */
    void startCleanup(Long assignmentId, Double latitude, Double longitude);

    // Upload after-cleanup image and validate using AI
    // The cleaner's captured position is required so proof from
    // outside the reported site can be rejected before any upload
    CleanupValidationResponse uploadCleanupImage(
            Long assignmentId,
            MultipartFile image,
            Double latitude,
            Double longitude
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
