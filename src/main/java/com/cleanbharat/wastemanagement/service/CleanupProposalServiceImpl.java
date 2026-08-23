package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.CreateProposalRequest;
import com.cleanbharat.wastemanagement.entity.CleanupApproval; // decision ledger row appended on resubmission
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision; // REVISION_SUBMITTED reopens the officer's buttons
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.*;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository; // append-only municipal decisions
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CleanupProposalRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.util.GeoLocationUtil; // Haversine distance helper
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional // Proposal row and assignment status move together, or not at all
public class CleanupProposalServiceImpl implements CleanupProposalService {

    private final CleanupProposalRepository proposalRepository;
    private final CleanupAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final CleanupApprovalRepository approvalRepository; // reads and appends the decision ledger
    private final CloudinaryService cloudinaryService; // optional inspection evidence upload

    /*
     * Clean Bharat platform rule (not a legal requirement): inspection evidence
     * is only accepted from within this radius of the reported site, so that a
     * proposal always reflects a real visit. Mirrors the cleanup-proof radius.
     */
    private static final double INSPECTION_RADIUS_METERS = 50.0;

    // Sites still open for competing proposals (nobody has been awarded the work yet)
    private static final Set<AssignmentStatus> OPEN_FOR_PROPOSALS =
            Set.of(AssignmentStatus.PENDING, AssignmentStatus.PROPOSAL_SUBMITTED);

    // Proposal states the owning cleaner is still allowed to edit or withdraw
    private static final Set<ProposalStatus> EDITABLE_STATES =
            Set.of(ProposalStatus.SUBMITTED, ProposalStatus.REVISION_REQUIRED);

    @Override
    public CleanupProposalResponse submitProposal(Long assignmentId, CreateProposalRequest request) {

        User cleaner = getLoggedInCleaner(); // role guard

        CleanupAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cleanup assignment not found"));

        GarbageReport report = assignment.getReport();

        validateAssignmentOpen(assignment); // cannot propose on awarded or finished work
        validateCleanerLocation(cleaner, report); // city and state jurisdiction check

        /*
         * The table is unique on (assignment_id, cleaner_id), so a cleaner only
         * ever holds ONE row per site.
         *
         * A live row blocks a second offer - the cleaner should edit or withdraw
         * the one they already have. A WITHDRAWN row must block nothing: the
         * cleaner stepped back while the site was still open, so they may bid
         * again for as long as nobody has been awarded the work. That old row is
         * therefore REVIVED below rather than duplicated, because a second row
         * would violate the unique constraint.
         */
        CleanupProposal existing = proposalRepository
                .findByAssignmentAndCleaner(assignment, cleaner)
                .orElse(null);

        if (existing != null && existing.getStatus() != ProposalStatus.WITHDRAWN) {
            throw new DuplicateProposalException(
                    "You have already submitted a proposal for this site. Edit or withdraw it instead.");
        }

        double distanceMeters = validateInspectionProximity(report,
                request.getInspectionLatitude(), request.getInspectionLongitude());

        String inspectionImageUrl = uploadInspectionImage(request.getInspectionImage()); // optional

        CleanupProposal proposal;

        if (existing != null) {
            // Re-proposing after a withdrawal: same row, freshly captured visit and plan
            existing.setSubmittedAt(LocalDateTime.now()); // ranks as a new bid in the officer's queue
            // A re-proposal is a brand new visit, so it always carries a freshly captured fix
            proposal = applyPlanDetails(existing, request, inspectionImageUrl, distanceMeters, true);
        } else {
            proposal = CleanupProposal.builder()
                    .assignment(assignment)
                    .cleaner(cleaner)
                    .inspectionImageUrl(inspectionImageUrl)
                    .inspectionLatitude(request.getInspectionLatitude())
                    .inspectionLongitude(request.getInspectionLongitude())
                    .inspectionDistanceMeters(distanceMeters >= 0 ? distanceMeters : null)
                    .inspectedAt(LocalDateTime.now()) // server clock, cleaner cannot backdate a visit
                    .siteObservations(request.getSiteObservations())
                    .estimatedDurationDays(request.getEstimatedDurationDays())
                    .manpowerCount(request.getManpowerCount())
                    .equipment(request.getEquipment())
                    .cleaningMethod(request.getCleaningMethod())
                    .wasteHandlingPlan(request.getWasteHandlingPlan())
                    .estimatedWasteVolume(request.getEstimatedWasteVolume())
                    .proposedStartDate(request.getProposedStartDate())
                    .remarks(request.getRemarks())
                    .status(ProposalStatus.SUBMITTED) // awaits municipal review
                    .build();
        }

        CleanupProposal saved = proposalRepository.save(proposal);

        /*
         * The first proposal only flags the site as "under consideration".
         * The cleaner field stays null on purpose: submitting a proposal never
         * awards the work, so other cleaners can keep competing.
         */
        if (assignment.getStatus() == AssignmentStatus.PENDING) {
            assignment.setStatus(AssignmentStatus.PROPOSAL_SUBMITTED);
            assignmentRepository.save(assignment);
        }

        return mapToResponse(saved);
    }

    @Override
    public CleanupProposalResponse updateProposal(Long proposalId, CreateProposalRequest request) {

        User cleaner = getLoggedInCleaner();
        CleanupProposal proposal = getOwnedProposal(proposalId, cleaner);

        if (!EDITABLE_STATES.contains(proposal.getStatus())) {
            throw new InvalidProposalStateException(
                    "This proposal can no longer be edited because it is " + proposal.getStatus() + ".");
        }

        CleanupAssignment assignment = proposal.getAssignment();
        validateAssignmentOpen(assignment); // site may have been awarded meanwhile

        // The officer is waiting for this edit, so the resubmission must unlock their review buttons
        boolean answeringRevisionRequest = proposal.getStatus() == ProposalStatus.REVISION_REQUIRED;

        /*
         * The cleaner already proved a real visit when this proposal was first
         * filed, so a revision may reuse the fix stored on the row. A new reading
         * is taken only when the cleaner chooses to capture their position again.
         */
        boolean freshReading = request.getInspectionLatitude() != null && request.getInspectionLongitude() != null;
        Double latitude = freshReading ? request.getInspectionLatitude() : proposal.getInspectionLatitude();
        Double longitude = freshReading ? request.getInspectionLongitude() : proposal.getInspectionLongitude();

        // The 50 m rule runs either way, so a stored fix can never be used to bypass it
        double distanceMeters = validateInspectionProximity(assignment.getReport(), latitude, longitude);

        String newImageUrl = uploadInspectionImage(request.getInspectionImage());

        // Same overwrite a revived proposal gets, so the two paths cannot drift apart
        applyPlanDetails(proposal, request, newImageUrl, distanceMeters, freshReading);

        CleanupProposal saved = proposalRepository.save(proposal);

        if (answeringRevisionRequest) {
            recordRevisionSubmitted(saved); // tells the municipal queue the answer has arrived
        }

        return mapToResponse(saved);
    }

    @Override
    public CleanupProposalResponse withdrawProposal(Long proposalId) {

        User cleaner = getLoggedInCleaner();
        CleanupProposal proposal = getOwnedProposal(proposalId, cleaner);

        if (!EDITABLE_STATES.contains(proposal.getStatus())) {
            throw new InvalidProposalStateException(
                    "This proposal can no longer be withdrawn because it is " + proposal.getStatus() + ".");
        }

        proposal.setStatus(ProposalStatus.WITHDRAWN); // kept for audit instead of deleted
        return mapToResponse(proposalRepository.save(proposal));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleanupProposalResponse> getMyProposals() {
        User cleaner = getLoggedInCleaner();
        return proposalRepository.findByCleanerOrderBySubmittedAtDesc(cleaner)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CleanupProposalResponse getProposal(Long proposalId) {
        User cleaner = getLoggedInCleaner();
        return mapToResponse(getOwnedProposal(proposalId, cleaner));
    }

    // ---------------------------------------------------------------- helpers

    // Resolves the JWT principal and rejects anyone who is not a cleaner
    private User getLoggedInCleaner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found"));

        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can submit cleanup proposals.");
        }
        return cleaner;
    }

    // A proposal is always fetched through its owner, so cleaners cannot read each other's bids
    private CleanupProposal getOwnedProposal(Long proposalId, User cleaner) {
        CleanupProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ProposalNotFoundException("Cleanup proposal not found"));

        if (!proposal.getCleaner().getId().equals(cleaner.getId())) {
            throw new UnauthorizedAssignmentAccessException("You can only manage your own cleanup proposals.");
        }
        return proposal;
    }

    // Proposals are only accepted while the site is unawarded
    private void validateAssignmentOpen(CleanupAssignment assignment) {
        if (assignment.getCleaner() != null || !OPEN_FOR_PROPOSALS.contains(assignment.getStatus())) {
            throw new InvalidProposalStateException(
                    "This cleanup site is no longer open for proposals.");
        }
    }

    // Cleaners work only inside their registered jurisdiction
    private void validateCleanerLocation(User cleaner, GarbageReport report) {
        if (!cleaner.getState().equalsIgnoreCase(report.getState())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only propose cleanups within your assigned state.");
        }
        if (!cleaner.getCity().equalsIgnoreCase(report.getCity())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only propose cleanups within your assigned city.");
        }
    }

    /*
     * Confirms the cleaner physically inspected the site and returns the measured
     * distance (-1 when the report itself has no coordinates to compare against).
     */
    private double validateInspectionProximity(GarbageReport report, Double latitude, Double longitude) {

        if (latitude == null || longitude == null || !Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new CleanerTooFarFromSiteException(
                    "We could not read your location. Please allow location access and inspect the site again.",
                    -1, INSPECTION_RADIUS_METERS);
        }

        if (report.getLatitude() == null || report.getLongitude() == null) {
            return -1; // legacy report without GPS: nothing to measure against
        }

        double distanceMeters = GeoLocationUtil.calculateDistanceMeters(
                latitude, longitude, report.getLatitude(), report.getLongitude());

        if (distanceMeters > INSPECTION_RADIUS_METERS) {
            throw new CleanerTooFarFromSiteException(
                    "You appear to be " + Math.round(distanceMeters) + " m away from the site. Inspect it from within "
                            + Math.round(INSPECTION_RADIUS_METERS) + " m before submitting a proposal.",
                    distanceMeters, INSPECTION_RADIUS_METERS);
        }
        return distanceMeters;
    }

    // Inspection evidence is optional, so an empty file simply means "no new image"
    private String uploadInspectionImage(MultipartFile inspectionImage) {
        if (inspectionImage == null || inspectionImage.isEmpty()) {
            return null;
        }
        return cloudinaryService.uploadFile(inspectionImage);
    }

    /*
     * Writes a freshly captured inspection and cleaning plan onto an existing row.
     *
     * Two paths mean the same thing to an officer - a revision of a live
     * proposal, and a re-proposal on a row the cleaner had withdrawn - so both
     * are filled in here and both end up SUBMITTED, back in the review queue.
     *
     * freshReading is false when a revision reuses the fix already on the row,
     * in which case the recorded visit must be left exactly as it was.
     */
    private CleanupProposal applyPlanDetails(CleanupProposal proposal,
                                             CreateProposalRequest request,
                                             String newImageUrl,
                                             double distanceMeters,
                                             boolean freshReading) {

        if (newImageUrl != null) {
            String oldImageUrl = proposal.getInspectionImageUrl();
            proposal.setInspectionImageUrl(newImageUrl);
            if (oldImageUrl != null) {
                cloudinaryService.deleteFile(oldImageUrl); // avoid orphan uploads
            }
        }

        if (freshReading) { // only a re-captured position may move the recorded visit
            proposal.setInspectionLatitude(request.getInspectionLatitude());
            proposal.setInspectionLongitude(request.getInspectionLongitude());
            proposal.setInspectedAt(LocalDateTime.now()); // server clock, cleaner cannot backdate a visit
        }
        // Measured against whichever fix was verified above, so the stored distance stays truthful
        proposal.setInspectionDistanceMeters(distanceMeters >= 0 ? distanceMeters : null);
        proposal.setSiteObservations(request.getSiteObservations());
        proposal.setEstimatedDurationDays(request.getEstimatedDurationDays());
        proposal.setManpowerCount(request.getManpowerCount());
        proposal.setEquipment(request.getEquipment());
        proposal.setCleaningMethod(request.getCleaningMethod());
        proposal.setWasteHandlingPlan(request.getWasteHandlingPlan());
        proposal.setEstimatedWasteVolume(request.getEstimatedWasteVolume());
        proposal.setProposedStartDate(request.getProposedStartDate());
        proposal.setRemarks(request.getRemarks());
        proposal.setStatus(ProposalStatus.SUBMITTED); // returns to the municipal review queue

        return proposal;
    }

    /*
     * Appends a REVISION_SUBMITTED row so the office that asked for changes can
     * see the answer arrived and act on it again. cleanup_approvals is
     * append-only, so the earlier REVISION_REQUIRED row survives as history.
     *
     * The row is keyed on the proposal, so the queue reads the signal for THIS
     * bid alone and the three officer buttons unlock only where they should.
     */
    private void recordRevisionSubmitted(CleanupProposal proposal) {

        CleanupAssignment assignment = proposal.getAssignment();

        // Newest proposal-stage row for this bid: the request we are answering
        Optional<CleanupApproval> lastDecision = approvalRepository
                .findFirstByProposalAndStageOrderByDecidedAtDescIdDesc(proposal, ApprovalStage.PROPOSAL);

        // Only a standing REVISION_REQUIRED may be answered, so no duplicate or out-of-order signal can land
        boolean revisionWasRequested = lastDecision
                .map(row -> row.getDecision() == ApprovalDecision.REVISION_REQUIRED)
                .orElse(false);

        if (!revisionWasRequested) {
            return; // nothing was asked for on this bid, so there is nothing to answer
        }

        /*
         * municipal_corporation_id is NOT NULL. A null here would raise
         * DataIntegrityViolationException and roll back the cleaner's whole
         * resubmission, so the office is resolved defensively: the assignment
         * first, then the office that actually raised the request.
         */
        var office = assignment.getAssignedMunicipalCorporation() != null
                ? assignment.getAssignedMunicipalCorporation() // office holding the site
                : lastDecision.get().getMunicipalCorporation(); // office that asked for changes

        if (office == null) {
            return; // no office to file under: skip the note rather than lose the edit
        }

        approvalRepository.save(CleanupApproval.builder()
                .assignment(assignment)
                .proposal(proposal) // proposal-scoped, so rival bids stay unaffected
                .stage(ApprovalStage.PROPOSAL)
                .decision(ApprovalDecision.REVISION_SUBMITTED)
                .municipalCorporation(office) // office that raised the request
                .remarks("Cleaner resubmitted the revised proposal.") // system note, no officer decided this
                .build());
    }

    // Flattens entity graph into the cleaner-facing DTO
    private CleanupProposalResponse mapToResponse(CleanupProposal proposal) {

        CleanupAssignment assignment = proposal.getAssignment();
        GarbageReport report = assignment.getReport();

        /*
         * Newest proposal-stage decision on THIS bid. Both dashboards read it to
         * agree on who holds the ball: the officer while it says
         * REVISION_REQUIRED, the cleaner once it says REVISION_SUBMITTED.
         */
        Optional<CleanupApproval> latestDecision = approvalRepository
                .findFirstByProposalAndStageOrderByDecidedAtDescIdDesc(proposal, ApprovalStage.PROPOSAL);

        return CleanupProposalResponse.builder()
                .proposalId(proposal.getId())
                .assignmentId(assignment.getId())
                .reportId(report.getId())
                .reportTitle(report.getTitle())
                .address(report.getAddress())
                .city(report.getCity())
                .assignmentStatus(assignment.getStatus() != null ? assignment.getStatus().name() : null)
                .cleanerId(proposal.getCleaner().getId())
                .cleanerName(proposal.getCleaner().getName())
                // Reviewers weigh a registered contractor differently from a lone
                // individual, so carry the bidder's category alongside the name
                .cleanerType(proposal.getCleaner().getCleanerType() != null
                        ? proposal.getCleaner().getCleanerType().name()
                        : null)
                .cleanerOrganization(proposal.getCleaner().getOrganizationName()) // null for individuals
                .inspectionImageUrl(proposal.getInspectionImageUrl())
                .inspectionLatitude(proposal.getInspectionLatitude())
                .inspectionLongitude(proposal.getInspectionLongitude())
                .inspectionDistanceMeters(proposal.getInspectionDistanceMeters())
                .inspectedAt(proposal.getInspectedAt())
                .siteObservations(proposal.getSiteObservations())
                .estimatedDurationDays(proposal.getEstimatedDurationDays())
                .manpowerCount(proposal.getManpowerCount())
                .equipment(proposal.getEquipment())
                .cleaningMethod(proposal.getCleaningMethod())
                .wasteHandlingPlan(proposal.getWasteHandlingPlan())
                .estimatedWasteVolume(proposal.getEstimatedWasteVolume())
                .proposedStartDate(proposal.getProposedStartDate())
                .remarks(proposal.getRemarks())
                .status(proposal.getStatus() != null ? proposal.getStatus().name() : null)
                .latestDecision(latestDecision.map(CleanupApproval::getDecision) // locks or frees the review buttons
                        .map(ApprovalDecision::name).orElse(null))
                .latestDecisionAt(latestDecision.map(CleanupApproval::getDecidedAt).orElse(null))
                .submittedAt(proposal.getSubmittedAt())
                .updatedAt(proposal.getUpdatedAt())
                .totalProposalsForAssignment(proposalRepository.countByAssignment(assignment)) // competition signal
                .build();
    }
}
