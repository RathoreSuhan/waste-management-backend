package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AICleanupVerificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.cleanbharat.wastemanagement.dto.ai.AICleanupVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional // Ensures all database updates succeed or roll back together
public class CleanupAssignmentServiceImpl implements CleanupAssignmentService {
    private final CleanupAssignmentRepository assignmentRepository;
    private final MunicipalCorporationRepository municipalRepository;
    private final UserRepository userRepository;

    // Upload images to Cloudinary
    private final CloudinaryService cloudinaryService;

    // AI validation service (Gemini implementation behind interface)
    private final AICleanupVerificationService aiCleanupVerificationService;

    // Reward service
    private final RewardService rewardService;

    // Public Feed Analytics
    private final PublicFeedAnalyticsService publicFeedAnalyticsService;

    @Override
    public void createDefaultAssignment(GarbageReport report) {

        // Prevent duplicate assignment creation
        if (assignmentRepository.existsByReport(report)) {
            return;
        }

        // Find Municipal Corporation based on report city
        MunicipalCorporation municipalCorporation =
                municipalRepository.findByCityIgnoreCase(report.getCity())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                        "No Municipal Corporation found for city: "
                                                + report.getCity()));

        CleanupAssignment assignment = CleanupAssignment.builder()
                .report(report)
                .assignedMunicipalCorporation(municipalCorporation)
                .status(AssignmentStatus.PENDING)
                .build();

        assignmentRepository.save(assignment);
    }

    @Override
    public void claimAssignment(Long assignmentId) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found"));

        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new InvalidAssignmentStateException("Only cleaners can claim assignments.");
        }

        CleanupAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        if (assignment.getCleaner() != null) {
            throw new AssignmentAlreadyClaimedException("Assignment already claimed.");
        }

        if (assignment.getStatus() != AssignmentStatus.PENDING) {
            throw new InvalidAssignmentStateException("Only pending assignments can be claimed.");
        }

        /*
         * Ensure cleaner belongs to the same
         * city and state as the garbage report.
         */
        validateCleanerLocation(cleaner, assignment.getReport());

        // Claim assignment
        assignment.setCleaner(cleaner);
        assignment.setStatus(AssignmentStatus.CLAIMED);
        assignment.setClaimedAt(LocalDateTime.now());

        assignmentRepository.save(assignment);
    }

    @Override
    public void startCleanup(Long assignmentId) {

        // Logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found"));

        // Only cleaners are allowed
        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can start cleanup.");}

        // Fetch assignment
        CleanupAssignment assignment = assignmentRepository.findById(assignmentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        // Assignment must already be claimed
        if (assignment.getStatus() != AssignmentStatus.CLAIMED) {
            throw new InvalidAssignmentStateException("Only claimed assignments can be started.");
        }

        // Logged-in cleaner must be the assigned cleaner
        if (assignment.getCleaner() == null
                || !assignment.getCleaner().getId().equals(cleaner.getId())) {

            throw new UnauthorizedAssignmentAccessException("You are not assigned to this cleanup task.");
        }

        // Update assignment
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setStartedAt(LocalDateTime.now());
        assignmentRepository.save(assignment);
    }

    @Override
    public CleanupValidationResponse uploadCleanupImage(Long assignmentId, MultipartFile image) {

        // Logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found"));

        // Fetch assignment
        CleanupAssignment assignment =
                assignmentRepository.findById(assignmentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        // Verify assigned cleaner
        if (assignment.getCleaner() == null
                || !assignment.getCleaner().getId().equals(cleaner.getId())) {

            throw new UnauthorizedAssignmentAccessException("You are not assigned to this cleanup task.");
        }

        /*
         * Cleanup has already been completed.
         *
         * Do not allow another upload.
         */
        if (assignment.getStatus() == AssignmentStatus.COMPLETED) {
            throw new AssignmentAlreadyCompletedException("This cleanup task has already been completed and AI verified. No further image uploads are allowed.");
        }

        /*
         * Cleanup must be started before
         * uploading completion image.
         */
        if (assignment.getStatus() != AssignmentStatus.IN_PROGRESS) {
            throw new CleanupNotStartedException("Cleanup must be started before uploading the completion image.");
        }

        /*
         * Cleaner may upload multiple images until
         * AI successfully verifies the cleanup.
         *
         * The rejected image is remembered here and removed only AFTER the
         * replacement upload succeeds, so a failed upload can never leave
         * the assignment with no image at all.
         */
        String previousCleanupImageUrl = assignment.getCleanupImageUrl(); // Image being replaced (null on first upload)

        // Upload cleaned image to Cloudinary
        String cleanupImageUrl = cloudinaryService.uploadFile(image);

        // Save uploaded image URL
        assignment.setCleanupImageUrl(cleanupImageUrl);

        /*
         * The replacement is now stored on the assignment, so the rejected
         * image is referenced by nothing and must be destroyed in Cloudinary
         * to avoid leaving unused files behind.
         */
        if (previousCleanupImageUrl != null
                && !previousCleanupImageUrl.isBlank()
                && !previousCleanupImageUrl.equals(cleanupImageUrl)) { // Never destroy the image just uploaded

            cloudinaryService.deleteFile(previousCleanupImageUrl);
        }

        /*
         * Validate BEFORE image
         * vs
         * AFTER image
         */
        AICleanupVerificationResponse aiResponse; // Assigned by the verification call below

        try {
            aiResponse = aiCleanupVerificationService.validateImages(
                            assignment.getReport().getImageUrl(),
                            cleanupImageUrl
                    );
        } catch (RuntimeException e) {
            /*
             * Verification could not be performed (image download failure,
             * AI outage, ...).
             *
             * This transaction rolls back, so the URL saved above never
             * reaches the database and nothing would ever reference the new
             * asset again. It is destroyed here to prevent that orphan.
             */
            cloudinaryService.deleteFile(cleanupImageUrl); // Remove the now unreferenced upload

            throw e; // Keep the original failure visible to the caller
        }

        /*
         * AI verification succeeds only when:
         *
         * 1. Same location
         * 2. Garbage removed
         * 3. Confidence >= 85%
         */
        assignment.setAiVerified(
                Boolean.TRUE.equals(aiResponse.getSameLocation())
                        && Boolean.TRUE.equals(aiResponse.getGarbageRemoved())
                        && aiResponse.getConfidence() != null
                        && aiResponse.getConfidence() >= 0.85
        );

        assignment.setAiConfidence(aiResponse.getConfidence());

        assignment.setAiRemarks(aiResponse.getRemarks());

        /*
         * AI approved cleanup
         */
        if (Boolean.TRUE.equals(assignment.getAiVerified())) {

            // Centralized cleanup completion logic
            resolveCleanupAssignment(assignment);
        }

        /*
         * AI rejected cleanup
         */
        else {

            /*
             * Keep assignment
             * IN_PROGRESS
             *
             * Cleaner can upload
             * another image.
             */
            assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        }

        assignmentRepository.save(assignment);

        return CleanupValidationResponse.builder()
                .aiVerified(assignment.getAiVerified()) // AI verification result
                .confidence(assignment.getAiConfidence()) // Confidence returned by Gemini
                .remarks(assignment.getAiRemarks()) // AI explanation
                .assignmentStatus(assignment.getStatus().name()) // Current assignment status

                // Current report status
                .reportStatus(
                        assignment.getReport()
                                .getStatus()
                                .name()
                )

                // User-friendly message
                .message(
                        Boolean.TRUE.equals(assignment.getAiVerified())
                                ? "Cleanup verified successfully. The report has been resolved."
                                : "The cleanup could not be verified with sufficient confidence. Please upload another clear image from the same location after completing the cleanup."
                )
                .build();
    }

    @Override
    public List<CleanupAssignmentResponse> getMyTasks() {

        // Currently logged-in cleaner
        User cleaner = getLoggedInCleaner();

        // Fetch all assignments of this cleaner
        return assignmentRepository.findByCleaner(cleaner)
                .stream()
                .map(this::mapToResponse)   // Entity → DTO
                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getPendingAssignments() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return assignmentRepository
                .findByCleanerIsNullAndStatus(AssignmentStatus.PENDING)
                .stream()

                // Same state
                .filter(assignment ->
                        cleaner.getState().equalsIgnoreCase(
                                assignment.getReport().getState()
                        )
                )

                // Same city
                .filter(assignment ->
                        cleaner.getCity().equalsIgnoreCase(
                                assignment.getReport().getCity()
                        )
                )

                // Entity -> DTO
                .map(this::mapToResponse)

                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getClaimedAssignments() {

        // Currently logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return assignmentRepository
                .findByCleanerAndStatus(cleaner, AssignmentStatus.CLAIMED)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getInProgressAssignments() {

        // Currently logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return assignmentRepository
                .findByCleanerAndStatus(cleaner, AssignmentStatus.IN_PROGRESS)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getCompletedAssignments() {

        // Currently logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return assignmentRepository
                .findByCleanerAndStatus(cleaner, AssignmentStatus.COMPLETED)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getNearbyAssignments() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        /*
         * Current implementation:
         * Nearby means same city & state.
         *
         * Future implementation:
         * Filter further using cleaner GPS
         * and sort by distance.
         */
        return assignmentRepository
                .findByCleanerIsNullAndStatus(AssignmentStatus.PENDING)
                .stream()

                // Same state
                .filter(assignment ->
                        cleaner.getState().equalsIgnoreCase(
                                assignment.getReport().getState()
                        )
                )

                // Same city
                .filter(assignment ->
                        cleaner.getCity().equalsIgnoreCase(
                                assignment.getReport().getCity()
                        )
                )

                // Entity -> DTO
                .map(this::mapToResponse)

                .toList();
    }

    /**
     * Marks a cleanup assignment as successfully completed.
     * This method centralizes all completion-related updates.
     */
    private void resolveCleanupAssignment(CleanupAssignment assignment) {

        // Mark assignment as completed
        assignment.setStatus(AssignmentStatus.COMPLETED);

        // Store completion time
        assignment.setCompletedAt(LocalDateTime.now());

        // Resolve original garbage report
        assignment.getReport().setStatus(ReportStatus.RESOLVED);

        /*
         * Reward the cleaner.
         *
         * RewardService is responsible for:
         * 1. Creating RewardHistory
         * 2. Updating total reward points
         */
        rewardService.rewardCleaner(assignment);

        // Initialize Public Feed Analytics
        publicFeedAnalyticsService.initializeAnalytics(assignment);
    }

    /**
     * Returns the currently authenticated cleaner.
     */
    private User getLoggedInCleaner() {

        // Logged-in user's authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Logged-in email
        String email = authentication.getName();

        // Fetch cleaner from database
        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found"));

        // Only cleaners can access cleaner dashboard APIs
        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can access these APIs.");
        }
        return cleaner;
    }


    /**
     * Ensures that a cleaner can only claim
     * assignments within their own city and state.
     */
    private void validateCleanerLocation(User cleaner, GarbageReport report){

        // State must match
        if (!cleaner.getState().equalsIgnoreCase(report.getState())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only claim cleanup assignments within your assigned state."
            );
        }

        // City must match
        if (!cleaner.getCity().equalsIgnoreCase(report.getCity())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only claim cleanup assignments within your assigned city."
            );
        }
    }


    /**
     * Converts CleanupAssignment entity into Dashboard DTO.
     */
    private CleanupAssignmentResponse mapToResponse(CleanupAssignment assignment) {

        return CleanupAssignmentResponse.builder()

                // Assignment details
                .assignmentId(assignment.getId())

                // Garbage report details
                .reportId(assignment.getReport().getId())
                .reportTitle(assignment.getReport().getTitle())
                .reportDescription(assignment.getReport().getDescription())

                // Before & After images
                .beforeImageUrl(assignment.getReport().getImageUrl())
                .afterImageUrl(assignment.getCleanupImageUrl())

                // Report location
                .address(assignment.getReport().getAddress())
                .city(assignment.getReport().getCity())

                // Assignment & report status
                .assignmentStatus(assignment.getStatus().name())
                .reportStatus(assignment.getReport().getStatus().name())

                // Cleaner name (null-safe)
                .cleanerName(
                        assignment.getCleaner() != null
                                ? assignment.getCleaner().getName()
                                : null
                )

                // Municipal Corporation
                .municipalCorporation(
                        assignment.getAssignedMunicipalCorporation()
                                .getOrganizationName()
                )

                // AI verification
                .aiVerified(assignment.getAiVerified())
                .aiConfidence(assignment.getAiConfidence())
                .aiRemarks(assignment.getAiRemarks())

                // Workflow timestamps
                .claimedAt(assignment.getClaimedAt())
                .startedAt(assignment.getStartedAt())
                .completedAt(assignment.getCompletedAt())

                // Report creation time
                .reportCreatedAt(assignment.getReport().getCreatedAt())

                .build();
    }
}