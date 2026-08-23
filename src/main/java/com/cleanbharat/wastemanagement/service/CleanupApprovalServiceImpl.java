package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ApprovalDecisionRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.dto.CleanupApprovalResponse;
import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalDashboardStatsResponse;
import com.cleanbharat.wastemanagement.entity.CleanupActivityLog;
import com.cleanbharat.wastemanagement.entity.CleanupApproval;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.exception.InvalidAssignmentStateException;
import com.cleanbharat.wastemanagement.exception.InvalidProposalStateException;
import com.cleanbharat.wastemanagement.exception.ProposalNotFoundException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CleanupProposalRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional; // latest ledger decision may be absent on a first-time bid
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Municipal approval workflow.
 * The municipality is the only authority that can authorise a cleaner (PROPOSAL stage)
 * and sign off finished work (COMPLETION stage).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CleanupApprovalServiceImpl implements CleanupApprovalService {

    private final CleanupApprovalRepository approvalRepository;
    private final CleanupAssignmentRepository assignmentRepository;
    private final CleanupProposalRepository proposalRepository;
    private final CleanupActivityLogRepository activityLogRepository; // read-only diary for the municipal body
    private final MunicipalCorporationRepository municipalRepository; // the signed-in corporation IS the authority
    private final RewardService rewardService;

    /*
     * The success story's counters.
     *
     * Created here rather than when the AI accepted the photograph, so a
     * cleanup only becomes publicly visible once the municipality has
     * officially signed it off.
     */
    private final PublicFeedAnalyticsService publicFeedAnalyticsService;

    // Proposals in these states are still "live" and can therefore be decided upon
    private static final Set<ProposalStatus> DECIDABLE_STATES = Set.of(
            ProposalStatus.SUBMITTED,
            ProposalStatus.REVISION_REQUIRED
    );

    // Work already awarded to a cleaner and still being executed on the ground
    private static final List<AssignmentStatus> ACTIVE_CLEANUP_STATUSES = List.of(
            AssignmentStatus.ASSIGNED,
            AssignmentStatus.CLAIMED,          // legacy rows from before the proposal workflow
            AssignmentStatus.IN_PROGRESS,
            AssignmentStatus.REWORK_REQUIRED   // sent back by the municipality, cleaner is redoing it
    );

    // ---------------------------------------------------------------------
    // PROPOSAL STAGE
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CleanupAssignmentResponse> getPendingReviewAssignments() {
        MunicipalCorporation corporation = getLoggedInCorporation();

        // Only assignments routed to this corporation's own city are visible
        List<CleanupAssignment> assignments = assignmentRepository
                .findByAssignedMunicipalCorporationAndStatusOrderByIdDesc(
                        corporation,
                        AssignmentStatus.PROPOSAL_SUBMITTED
                );

        return assignments.stream()
                .map(this::mapAssignment)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleanupProposalResponse> getProposalsForAssignment(Long assignmentId) {
        MunicipalCorporation corporation = getLoggedInCorporation();
        CleanupAssignment assignment = getAssignment(assignmentId);
        assertSameCorporation(corporation, assignment); // jurisdiction guard

        return proposalRepository.findByAssignmentOrderBySubmittedAtAsc(assignment).stream()
                .map(this::mapProposal)
                .collect(Collectors.toList());
    }

    @Override
    public CleanupApprovalResponse decideProposal(Long proposalId, ApprovalDecisionRequest request) {
        MunicipalCorporation corporation = getLoggedInCorporation();

        CleanupProposal proposal = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new ProposalNotFoundException("Proposal not found with id: " + proposalId));

        CleanupAssignment assignment = proposal.getAssignment();
        assertSameCorporation(corporation, assignment); // a city's body cannot decide another city's work

        // Withdrawn / already decided proposals cannot be reviewed again
        if (!DECIDABLE_STATES.contains(proposal.getStatus())) {
            throw new InvalidProposalStateException(
                    "This proposal is no longer open for review. Current status: " + proposal.getStatus().name()
            );
        }

        // Only one proposal per assignment may ever be authorised
        if (approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)) {
            throw new InvalidProposalStateException(
                    "A cleaner has already been authorised for this cleanup. No further proposal approvals are allowed."
            );
        }

        /*
         * A revision request hands the ball to the cleaner. Until they resubmit
         * (which appends REVISION_SUBMITTED) no second decision may be recorded
         * on this proposal, so the same cleaner cannot be sent two answers.
         */
        if (isAwaitingRevision(proposal)) {
            throw new InvalidProposalStateException(
                    "You have already asked this cleaner for a revision. Wait until the revised proposal arrives."
            );
        }

        ApprovalDecision decision = request.getDecision();

        switch (decision) {
            case APPROVED -> {
                // Winning proposal
                proposal.setStatus(ProposalStatus.APPROVED);
                proposalRepository.save(proposal);

                // Every other live proposal on this assignment loses
                List<CleanupProposal> others = proposalRepository.findByAssignmentOrderBySubmittedAtAsc(assignment);
                for (CleanupProposal other : others) {
                    if (!other.getId().equals(proposal.getId()) && DECIDABLE_STATES.contains(other.getStatus())) {
                        other.setStatus(ProposalStatus.REJECTED); // auto-rejected: work already awarded
                        proposalRepository.save(other);
                    }
                }

                // Municipality assigns the approved cleaner to the assignment
                assignment.setCleaner(proposal.getCleaner());
                assignment.setClaimedAt(LocalDateTime.now()); // moment the work was awarded
                assignment.setStatus(AssignmentStatus.ASSIGNED);
                assignmentRepository.save(assignment);
            }
            case REJECTED -> {
                proposal.setStatus(ProposalStatus.REJECTED);
                proposalRepository.save(proposal);

                // If nothing is left to review, reopen the assignment for fresh proposals
                if (countLiveProposals(assignment) == 0) {
                    assignment.setStatus(AssignmentStatus.PENDING);
                    assignmentRepository.save(assignment);
                }
            }
            case REVISION_REQUIRED -> {
                // Cleaner may edit and resubmit; assignment stays under review
                proposal.setStatus(ProposalStatus.REVISION_REQUIRED);
                proposalRepository.save(proposal);
            }
        }

        // Append-only audit record of the municipal decision
        CleanupApproval approval = CleanupApproval.builder()
                .assignment(assignment)
                .proposal(proposal)
                .stage(ApprovalStage.PROPOSAL)
                .decision(decision)
                .municipalCorporation(assignment.getAssignedMunicipalCorporation())
                // No separate officer account exists: the corporation itself is the decider
                .remarks(request.getRemarks())
                .build();

        return mapApproval(approvalRepository.save(approval));
    }

    // ---------------------------------------------------------------------
    // COMPLETION STAGE
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CleanupAssignmentResponse> getPendingCompletionAssignments() {
        MunicipalCorporation corporation = getLoggedInCorporation();

        List<CleanupAssignment> assignments = assignmentRepository
                .findByAssignedMunicipalCorporationAndStatusOrderByIdDesc(
                        corporation,
                        AssignmentStatus.AWAITING_APPROVAL
                );

        return assignments.stream()
                .map(this::mapAssignment)
                .collect(Collectors.toList());
    }

    @Override
    public CleanupApprovalResponse decideCompletion(Long assignmentId, ApprovalDecisionRequest request) {
        MunicipalCorporation corporation = getLoggedInCorporation();
        CleanupAssignment assignment = getAssignment(assignmentId);
        assertSameCorporation(corporation, assignment); // jurisdiction guard

        // Only AI-verified uploads waiting for sign-off can be decided here
        if (assignment.getStatus() != AssignmentStatus.AWAITING_APPROVAL) {
            throw new InvalidAssignmentStateException(
                    "Only cleanups awaiting municipal approval can be reviewed. Current status: "
                            + assignment.getStatus().name()
            );
        }

        ApprovalDecision decision = request.getDecision();

        if (decision == ApprovalDecision.APPROVED) {
            // Final sign-off: this is the ONLY place a cleanup becomes COMPLETED
            assignment.setStatus(AssignmentStatus.COMPLETED);
            assignment.setCompletedAt(LocalDateTime.now());

            GarbageReport report = assignment.getReport();
            report.setStatus(ReportStatus.RESOLVED); // citizen's report is officially closed
            assignmentRepository.save(assignment);

            rewardService.rewardCleaner(assignment); // reward released only after municipal approval

            // Success story goes live only now, with the municipality's sign-off behind it
            publicFeedAnalyticsService.initializeAnalytics(assignment);
        } else {
            // Rejected or revision requested: the job stays with the same cleaner but is
            // parked in a dedicated rework state, so the cleaner continues cleaning and
            // re-submits proof (GPS + AI run again) for a fresh municipal review.
            assignment.setStatus(AssignmentStatus.REWORK_REQUIRED);
            assignment.setCompletedAt(null); // not finished after all
            assignmentRepository.save(assignment);
        }

        CleanupApproval approval = CleanupApproval.builder()
                .assignment(assignment)
                .proposal(null) // completion decisions are not tied to a single proposal
                .stage(ApprovalStage.COMPLETION)
                .decision(decision)
                .municipalCorporation(assignment.getAssignedMunicipalCorporation())
                // No separate officer account exists: the corporation itself is the decider
                .remarks(request.getRemarks())
                .build();

        return mapApproval(approvalRepository.save(approval));
    }

    // ---------------------------------------------------------------------
    // HISTORY
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<CleanupApprovalResponse> getApprovalHistory(Long assignmentId) {
        MunicipalCorporation corporation = getLoggedInCorporation();
        CleanupAssignment assignment = getAssignment(assignmentId);
        assertSameCorporation(corporation, assignment); // jurisdiction guard

        return approvalRepository.findByAssignmentOrderByDecidedAtAsc(assignment).stream()
                .map(this::mapApproval)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // MUNICIPAL DASHBOARD READS (own corporation only)
    // ---------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MunicipalDashboardStatsResponse getDashboardStats() {
        MunicipalCorporation corporation = getLoggedInCorporation(); // jurisdiction of the signed-in body

        return MunicipalDashboardStatsResponse.builder()
                .corporationName(corporation.getOrganizationName())
                .city(corporation.getCity()) // one corporation per city, so the city is its whole jurisdiction
                // Every site routed to this corporation
                .relevantReports(assignmentRepository.countByAssignedMunicipalCorporation(corporation))
                // Sites where cleaners are bidding and a decision is due
                .pendingProposals(assignmentRepository.countByAssignedMunicipalCorporationAndStatus(
                        corporation, AssignmentStatus.PROPOSAL_SUBMITTED))
                // Awarded work being executed (includes rework)
                .activeCleanups(assignmentRepository.countByAssignedMunicipalCorporationAndStatusIn(
                        corporation, ACTIVE_CLEANUP_STATUSES))
                // Evidence submitted, waiting for the officer's final sign-off
                .completionReviews(assignmentRepository.countByAssignedMunicipalCorporationAndStatus(
                        corporation, AssignmentStatus.AWAITING_APPROVAL))
                .completedCleanups(assignmentRepository.countByAssignedMunicipalCorporationAndStatus(
                        corporation, AssignmentStatus.COMPLETED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleanupAssignmentResponse> getActiveCleanups() {
        MunicipalCorporation corporation = getLoggedInCorporation();

        return assignmentRepository
                .findByAssignedMunicipalCorporationAndStatusInOrderByIdDesc(corporation, ACTIVE_CLEANUP_STATUSES)
                .stream()
                .map(this::mapAssignment)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CleanupAssignmentResponse getAssignmentForReview(Long assignmentId) {
        MunicipalCorporation corporation = getLoggedInCorporation();
        CleanupAssignment assignment = getAssignment(assignmentId);
        assertSameCorporation(corporation, assignment); // jurisdiction guard

        return mapAssignment(assignment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CleanupActivityLogResponse> getAssignmentActivityLogs(Long assignmentId) {
        MunicipalCorporation corporation = getLoggedInCorporation();
        CleanupAssignment assignment = getAssignment(assignmentId);
        assertSameCorporation(corporation, assignment); // jurisdiction guard

        // The municipal body reads the diary as supporting evidence; only cleaners can write entries
        return activityLogRepository.findByAssignmentOrderByActivityAtAsc(assignment).stream()
                .map(this::mapActivityLog)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    /**
     * Resolves the Municipal Corporation behind the current token.
     *
     * Only the official email the admin registered for a city exists in this table,
     * so a normal account (even a cleaner of type MUNICIPAL) can never reach here.
     */
    private MunicipalCorporation getLoggedInCorporation() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();

        return municipalRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UnauthorizedAssignmentAccessException(
                        "Only a registered Municipal Corporation can review cleanup approvals."));
    }

    /** A corporation may only act on work routed to itself. */
    private void assertSameCorporation(MunicipalCorporation corporation, CleanupAssignment assignment) {
        if (assignment.getAssignedMunicipalCorporation() == null
                || !corporation.getId()
                        .equals(assignment.getAssignedMunicipalCorporation().getId())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You can only review cleanups belonging to your own Municipal Corporation."
            );
        }
    }

    private CleanupAssignment getAssignment(Long assignmentId) {
        return assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cleanup assignment not found with id: " + assignmentId));
    }

    /** Live proposals are the ones still waiting for a municipal decision. */
    private long countLiveProposals(CleanupAssignment assignment) {
        return proposalRepository.countByAssignmentAndStatus(assignment, ProposalStatus.SUBMITTED)
                + proposalRepository.countByAssignmentAndStatus(assignment, ProposalStatus.REVISION_REQUIRED);
    }

    // ---------------------------------------------------------------------
    // Mappers
    // ---------------------------------------------------------------------

    private CleanupAssignmentResponse mapAssignment(CleanupAssignment assignment) {
        GarbageReport report = assignment.getReport();

        return CleanupAssignmentResponse.builder()
                .assignmentId(assignment.getId())
                // Garbage report details
                .reportId(report.getId())
                .reportTitle(report.getTitle())
                .reportDescription(report.getDescription())
                .beforeImageUrl(report.getImageUrl())
                .afterImageUrl(assignment.getCleanupImageUrl())
                // Report location
                .address(report.getAddress())
                .city(report.getCity())
                .reportLatitude(report.getLatitude())
                .reportLongitude(report.getLongitude())
                // Workflow state
                .assignmentStatus(assignment.getStatus().name())
                .reportStatus(report.getStatus().name())
                // Awarded cleaner identity - the officer must know who is accountable
                .cleanerName(assignment.getCleaner() != null ? assignment.getCleaner().getName() : null)
                .cleanerId(assignment.getCleaner() != null ? assignment.getCleaner().getId() : null)
                .cleanerEmail(assignment.getCleaner() != null ? assignment.getCleaner().getEmail() : null)
                .cleanerType(assignment.getCleaner() != null && assignment.getCleaner().getCleanerType() != null
                        ? assignment.getCleaner().getCleanerType().name()
                        : null)
                .cleanerOrganization(assignment.getCleaner() != null
                        ? assignment.getCleaner().getOrganizationName()
                        : null)
                .municipalCorporation(assignment.getAssignedMunicipalCorporation() != null
                        ? assignment.getAssignedMunicipalCorporation().getOrganizationName()
                        : null)
                // AI verification snapshot - advisory input for the officer, never the decision
                .aiVerified(assignment.getAiVerified())
                .aiConfidence(assignment.getAiConfidence())
                .aiRemarks(assignment.getAiRemarks())
                // Workflow timestamps
                .claimedAt(assignment.getClaimedAt())
                .startedAt(assignment.getStartedAt())
                // Recorded GPS proof captured when work started (checked against the 50 m rule)
                .startLatitude(assignment.getStartLatitude())
                .startLongitude(assignment.getStartLongitude())
                .startDistanceMeters(assignment.getStartDistanceMeters())
                .completedAt(assignment.getCompletedAt())
                // Supporting-evidence counters used by the review screens
                .activityLogCount(activityLogRepository
                        .findByAssignmentOrderByActivityAtAsc(assignment).size())
                .proposalCount(proposalRepository.countByAssignment(assignment))
                .reportCreatedAt(report.getCreatedAt())
                .build();
    }

    /** Read-only projection of one diary entry for the municipal review screens. */
    private CleanupActivityLogResponse mapActivityLog(CleanupActivityLog log) {
        return CleanupActivityLogResponse.builder()
                .activityLogId(log.getId())
                .assignmentId(log.getAssignment() != null ? log.getAssignment().getId() : null)
                .description(log.getDescription())
                .activityAt(log.getActivityAt())
                .imageUrl(log.getImageUrl())
                // Where the entry was recorded, so the officer can sanity-check it against the site
                .latitude(log.getLatitude())
                .longitude(log.getLongitude())
                .distanceMeters(log.getDistanceMeters())
                .cleanerName(log.getCleaner() != null ? log.getCleaner().getName() : null)
                .createdAt(log.getCreatedAt())
                .build();
    }

    private CleanupProposalResponse mapProposal(CleanupProposal proposal) {
        CleanupAssignment assignment = proposal.getAssignment();
        GarbageReport report = assignment.getReport();

        return CleanupProposalResponse.builder()
                .proposalId(proposal.getId())
                .assignmentId(assignment.getId())
                // Garbage report details
                .reportId(report.getId())
                .reportTitle(report.getTitle())
                .address(report.getAddress())
                .city(report.getCity())
                .assignmentStatus(assignment.getStatus() != null ? assignment.getStatus().name() : null)
                // Bidding cleaner
                .cleanerId(proposal.getCleaner() != null ? proposal.getCleaner().getId() : null)
                .cleanerName(proposal.getCleaner() != null ? proposal.getCleaner().getName() : null)

                // Cleaner category and organisation: an officer weighs an NGO or a
                // private contractor differently from a lone individual cleaner
                .cleanerType(proposal.getCleaner() != null && proposal.getCleaner().getCleanerType() != null
                        ? proposal.getCleaner().getCleanerType().name()
                        : null)
                .cleanerOrganization(proposal.getCleaner() != null
                        ? proposal.getCleaner().getOrganizationName()
                        : null)
                // On-site inspection evidence
                .inspectionImageUrl(proposal.getInspectionImageUrl())
                .inspectionLatitude(proposal.getInspectionLatitude())
                .inspectionLongitude(proposal.getInspectionLongitude())
                .inspectionDistanceMeters(proposal.getInspectionDistanceMeters())
                .inspectedAt(proposal.getInspectedAt())
                // Cleanup plan
                .siteObservations(proposal.getSiteObservations())
                .estimatedDurationDays(proposal.getEstimatedDurationDays())
                .manpowerCount(proposal.getManpowerCount())
                .equipment(proposal.getEquipment())
                .cleaningMethod(proposal.getCleaningMethod())
                .wasteHandlingPlan(proposal.getWasteHandlingPlan())
                .estimatedWasteVolume(proposal.getEstimatedWasteVolume())
                .proposedStartDate(proposal.getProposedStartDate())
                .remarks(proposal.getRemarks())
                // Review state
                .status(proposal.getStatus() != null ? proposal.getStatus().name() : null)
                // Newest decision on this bid: the review buttons stay locked while it
                // reads REVISION_REQUIRED and open again on REVISION_SUBMITTED
                .latestDecision(latestProposalDecision(proposal)
                        .map(CleanupApproval::getDecision)
                        .map(ApprovalDecision::name)
                        .orElse(null))
                .latestDecisionAt(latestProposalDecision(proposal)
                        .map(CleanupApproval::getDecidedAt)
                        .orElse(null))
                .submittedAt(proposal.getSubmittedAt())
                .updatedAt(proposal.getUpdatedAt())
                .totalProposalsForAssignment(proposalRepository.countByAssignment(assignment))
                .build();
    }

    /** Newest proposal-stage decision recorded against one bid, if any. */
    private Optional<CleanupApproval> latestProposalDecision(CleanupProposal proposal) {
        return approvalRepository
                .findFirstByProposalAndStageOrderByDecidedAtDescIdDesc(proposal, ApprovalStage.PROPOSAL);
    }

    /**
     * True while the officer is waiting on a revised proposal from the cleaner.
     *
     * Read from the append-only ledger rather than the proposal status, because
     * a resubmission puts the row back to SUBMITTED and would otherwise look
     * indistinguishable from a first-time bid.
     */
    private boolean isAwaitingRevision(CleanupProposal proposal) {
        return latestProposalDecision(proposal)
                .map(CleanupApproval::getDecision)
                .filter(decision -> decision == ApprovalDecision.REVISION_REQUIRED)
                .isPresent();
    }

    private CleanupApprovalResponse mapApproval(CleanupApproval approval) {
        CleanupAssignment assignment = approval.getAssignment();

        return CleanupApprovalResponse.builder()
                .approvalId(approval.getId())
                .assignmentId(assignment != null ? assignment.getId() : null)
                .proposalId(approval.getProposal() != null ? approval.getProposal().getId() : null)
                .reportId(assignment != null && assignment.getReport() != null ? assignment.getReport().getId() : null)
                .reportTitle(assignment != null && assignment.getReport() != null
                        ? assignment.getReport().getTitle()
                        : null)
                .stage(approval.getStage())
                .decision(approval.getDecision())
                .remarks(approval.getRemarks())
                // Decisions are signed by the corporation, so its name is the fallback decider
                .decidedByName(approval.getDecidedBy() != null
                        ? approval.getDecidedBy().getName()
                        : (approval.getMunicipalCorporation() != null
                                ? approval.getMunicipalCorporation().getOrganizationName()
                                : null))
                .municipalCorporationName(approval.getMunicipalCorporation() != null
                        ? approval.getMunicipalCorporation().getOrganizationName()
                        : null)
                .decidedAt(approval.getDecidedAt())
                .build();
    }
}