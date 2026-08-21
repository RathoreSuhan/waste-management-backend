package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AICleanupVerificationService;
import com.cleanbharat.wastemanagement.util.GeoLocationUtil; // Haversine distance helper
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

    // Municipal decisions: used to prove a cleaner was actually authorised
    private final CleanupApprovalRepository approvalRepository;

    private final MunicipalCorporationRepository municipalRepository;
    private final UserRepository userRepository;

    // Upload images to Cloudinary
    private final CloudinaryService cloudinaryService;

    // AI validation service (Gemini implementation behind interface)
    private final AICleanupVerificationService aiCleanupVerificationService;

    /*
     * Rewards and the public feed entry are no longer created here.
     *
     * Both are released by CleanupApprovalService after a municipal officer
     * approves the COMPLETION stage, so nothing is paid out - and nothing is
     * published as a success story - before the municipality has signed the
     * cleanup off.
     */

    /*
     * Radius within which cleanup proof is accepted, in metres.
     *
     * Mirrored by CLEANUP_PROOF_RADIUS_METRES in the cleaner UI, which warns
     * the cleaner before the upload. This value is the one that decides.
     */
    private static final double CLEANUP_PROOF_RADIUS_METERS = 50.0;

    /*
     * Sites a cleaner may still act on: nobody has been awarded the work yet,
     * so both PENDING and PROPOSAL_SUBMITTED stay visible for proposals.
     */
    private static final List<AssignmentStatus> OPEN_FOR_PROPOSAL_STATUSES =
            List.of(AssignmentStatus.PENDING, AssignmentStatus.PROPOSAL_SUBMITTED);

    /**
     * States in which the authorised cleaner is actively working on site.
     *
     * REWORK_REQUIRED belongs here because a municipal rework request does not
     * take the job away: the cleaner simply continues cleaning and re-submits
     * proof, which runs GPS + AI again exactly like the first attempt.
     */
    private static final List<AssignmentStatus> WORK_IN_PROGRESS_STATUSES =
            List.of(AssignmentStatus.IN_PROGRESS, AssignmentStatus.REWORK_REQUIRED);

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

    /*
     * claimAssignment() was removed with the municipal-authorized workflow.
     *
     * A cleaner can no longer self-assign a site: they inspect it and submit a
     * proposal (see CleanupProposalService), and a municipal officer decides
     * who is awarded the work.
     */

    @Override
    public void startCleanup(Long assignmentId, Double latitude, Double longitude) { // start location evidence

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
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED && assignment.getStatus() != AssignmentStatus.CLAIMED) { // ASSIGNED = municipality approved; CLAIMED kept for legacy rows
            throw new InvalidAssignmentStateException("Only approved cleanups can be started.");
        }

        // Logged-in cleaner must be the assigned cleaner
        if (assignment.getCleaner() == null
                || !assignment.getCleaner().getId().equals(cleaner.getId())) {

            throw new UnauthorizedAssignmentAccessException("You are not assigned to this cleanup task.");
        }

        /*
         * A cleaner may only begin once the municipality has authorised their
         * proposal. Legacy CLAIMED rows predate the approval workflow, so they
         * are exempt from this check.
         */
        if (assignment.getStatus() == AssignmentStatus.ASSIGNED
                && !approvalRepository.existsByAssignmentAndStageAndDecision(
                        assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)) {

            throw new InvalidAssignmentStateException(
                    "This cleanup has not been authorised by the Municipal Corporation yet."
            );
        }

        /*
         * Start-of-work location evidence.
         *
         * Reuses the same 50 m rule as the proof upload, so "work started" can
         * only be recorded from the site itself.
         */
        validateCleanerProximity(assignment.getReport(), latitude, longitude);

        // Update assignment
        assignment.setStatus(AssignmentStatus.IN_PROGRESS);
        assignment.setStartedAt(LocalDateTime.now());

        // Persist where the cleaner stood when they began
        assignment.setStartLatitude(latitude);
        assignment.setStartLongitude(longitude);
        assignment.setStartDistanceMeters(
                distanceFromReport(assignment.getReport(), latitude, longitude)); // null when the report has no coordinates

        assignmentRepository.save(assignment);
    }

    @Override
    public CleanupValidationResponse uploadCleanupImage(
            Long assignmentId,
            MultipartFile image,
            Double latitude,  // Cleaner's captured latitude
            Double longitude  // Cleaner's captured longitude
    ) {

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
         * Cleanup must be started before uploading completion image.
         *
         * REWORK_REQUIRED is accepted too: the municipality sent the work back,
         * so the same cleaner continues on site and re-submits proof through
         * this very method, which re-runs GPS and AI from scratch.
         */
        if (!WORK_IN_PROGRESS_STATUSES.contains(assignment.getStatus())) {
            throw new CleanupNotStartedException("Cleanup must be started before uploading the completion image.");
        }

        // Remembered so a rejected re-submission stays labelled as a re-do
        AssignmentStatus workingStatus = assignment.getStatus();

        /*
         * Proof of presence.
         *
         * The cleaner's captured position is checked against the citizen's
         * reported coordinates first, so a photograph taken away from the
         * site never reaches Cloudinary or the AI.
         */
        validateCleanerProximity(assignment.getReport(), latitude, longitude);

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
             * Keep the assignment in whichever working state it arrived in
             * (IN_PROGRESS on a first attempt, REWORK_REQUIRED on a re-do) so
             * the cleaner can upload another image without the municipal
             * rework instruction disappearing from their task list.
             */
            assignment.setStatus(workingStatus);
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
                                ? "Cleanup verified successfully. It is now awaiting Municipal Corporation approval."
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
                .map(this::mapToResponse)   // Entity â†’ DTO
                .toList();
    }

    @Override
    public List<CleanupAssignmentResponse> getPendingAssignments() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return assignmentRepository
                .findByCleanerIsNullAndStatusInOrderByIdDesc(OPEN_FOR_PROPOSAL_STATUSES)
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

        // A first attempt and a municipal re-do are both live work
        return assignmentRepository
                .findByCleanerAndStatusIn(cleaner, WORK_IN_PROGRESS_STATUSES)
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
                .findByCleanerIsNullAndStatusInOrderByIdDesc(OPEN_FOR_PROPOSAL_STATUSES)
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
     * Hands an AI-verified cleanup over to the Municipal Corporation for sign-off.
     *
     * The cleanup is not finished yet: COMPLETED, report resolution, the
     * cleaner's reward and the public feed entry are all released by
     * CleanupApprovalService once an officer approves the COMPLETION stage.
     */
    private void resolveCleanupAssignment(CleanupAssignment assignment) {

        // Proof accepted by AI, now queued for municipal approval
        assignment.setStatus(AssignmentStatus.AWAITING_APPROVAL);

        // Moment the cleaner submitted verified proof
        assignment.setCompletedAt(LocalDateTime.now());

        /*
         * The public feed entry used to be created here, the moment the AI
         * accepted the photograph. That published the cleanup as a finished
         * success story while the municipality had not yet looked at it - and
         * a cleanup sent back for rework would already be on the feed. It is
         * now created only on municipal approval.
         */
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
     * Ensures the cleaner is standing at the reported site when uploading proof.
     *
     * The position comes from the cleaner's device; the UI offers no manual
     * entry, and a request arriving without a usable position is refused here
     * so the check cannot simply be skipped.
     */
    private void validateCleanerProximity(GarbageReport report, Double latitude, Double longitude) {

        // Without a readable position, presence cannot be established
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {

            throw new CleanerTooFarFromSiteException(
                    "Your current location could not be read. Please allow location access and capture your position at the site before uploading the cleanup image.",
                    -1,                             // Distance unknown
                    CLEANUP_PROOF_RADIUS_METERS
            );
        }

        // Reports filed before coordinate capture cannot be measured against
        if (report.getLatitude() == null || report.getLongitude() == null) {
            return;
        }

        // Straight-line distance from the cleaner to the reported location
        double distanceMeters = GeoLocationUtil.calculateDistanceMeters(
                latitude,
                longitude,
                report.getLatitude(),
                report.getLongitude()
        );

        if (distanceMeters > CLEANUP_PROOF_RADIUS_METERS) {

            // Numbers are named in the message so the cleaner knows how far to move
            throw new CleanerTooFarFromSiteException(
                    "You appear to be " + Math.round(distanceMeters)
                            + " m away from the reported location. Cleanup proof is accepted only within "
                            + Math.round(CLEANUP_PROOF_RADIUS_METERS)
                            + " m of the site.",
                    distanceMeters,
                    CLEANUP_PROOF_RADIUS_METERS
            );
        }
    }


    /**
     * Distance in metres from a captured position to the reported location.
     *
     * Returns null when either side has no coordinates, so an unmeasurable
     * reading is stored as "unknown" rather than a misleading zero.
     */
    private Double distanceFromReport(GarbageReport report, Double latitude, Double longitude) {

        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || report.getLatitude() == null || report.getLongitude() == null) {

            return null;
        }

        return GeoLocationUtil.calculateDistanceMeters(
                latitude,
                longitude,
                report.getLatitude(),
                report.getLongitude()
        );
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

                // Coordinates the cleaner app measures its distance against
                .reportLatitude(assignment.getReport().getLatitude())
                .reportLongitude(assignment.getReport().getLongitude())

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

                // Start-of-work location evidence (Phase 16)
                .startLatitude(assignment.getStartLatitude())
                .startLongitude(assignment.getStartLongitude())

                // Optional diary size, so the task card can label its button
                .activityLogCount(
                        assignment.getActivityLogs() != null
                                ? assignment.getActivityLogs().size()
                                : 0
                )

                // Report creation time
                .reportCreatedAt(assignment.getReport().getCreatedAt())

                .build();
    }
}
