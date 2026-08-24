package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ApprovalDecisionRequest;
import com.cleanbharat.wastemanagement.dto.CleanupApprovalResponse;
import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.entity.CleanupApproval;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidAssignmentStateException;
import com.cleanbharat.wastemanagement.exception.InvalidProposalStateException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CleanupProposalRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the municipal approval workflow.
 * Focus: only the Municipal Corporation whose official email the admin registered can
 * authorise a cleaner (PROPOSAL stage) and sign off finished work (COMPLETION stage),
 * and it may only do so for assignments routed to its own city.
 */
@ExtendWith(MockitoExtension.class)
class CleanupApprovalServiceTest {

    private static final String CORPORATION_EMAIL = "mcmovali@gmail.com"; // registered by the admin for Mohali
    private static final String CORPORATION_CITY = "Mohali";
    private static final long CORPORATION_ID = 10L;
    private static final long ASSIGNMENT_ID = 41L;
    private static final long WINNING_PROPOSAL_ID = 91L;
    private static final long LOSING_PROPOSAL_ID = 92L;

    @Mock private CleanupApprovalRepository approvalRepository;
    @Mock private CleanupAssignmentRepository assignmentRepository;
    @Mock private CleanupProposalRepository proposalRepository;
    @Mock private CleanupActivityLogRepository activityLogRepository; // municipal views count diary entries
    @Mock private MunicipalCorporationRepository municipalRepository; // the signed-in corporation IS the authority
    @Mock private RewardService rewardService;
    @Mock private PublicFeedAnalyticsService publicFeedAnalyticsService; // success story goes live after sign-off

    @InjectMocks private CleanupApprovalServiceImpl cleanupApprovalService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // tests share one thread, so never leak a principal
    }

    // ---------------------------------------------------------------------
    // PROPOSAL STAGE
    // ---------------------------------------------------------------------

    @Test
    void approvingProposalAssignsThatCleanerToTheAssignment() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        User cleaner = cleaner(5L, "cleaner.one@example.com");
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal winner = proposal(WINNING_PROPOSAL_ID, assignment, cleaner, ProposalStatus.SUBMITTED);

        signInAsCorporation(corporation);
        when(proposalRepository.findById(WINNING_PROPOSAL_ID)).thenReturn(Optional.of(winner));
        when(proposalRepository.findByAssignmentOrderBySubmittedAtAsc(assignment)).thenReturn(List.of(winner));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        CleanupApprovalResponse response = cleanupApprovalService.decideProposal(
                WINNING_PROPOSAL_ID, request(ApprovalDecision.APPROVED, "Plan looks solid"));

        assertEquals(ProposalStatus.APPROVED, winner.getStatus());              // winning bid
        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus());        // moved into the work state
        assertSame(cleaner, assignment.getCleaner());                           // corporation picked the cleaner
        assertEquals(ApprovalStage.PROPOSAL, response.getStage());
        assertEquals(ApprovalDecision.APPROVED, response.getDecision());
        assertEquals(WINNING_PROPOSAL_ID, response.getProposalId());
        // The corporation itself signs the decision, so its name is reported as the decider
        assertEquals(corporation.getOrganizationName(), response.getDecidedByName());
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void approvingOneProposalAutoRejectsTheOtherLiveProposals() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal winner = proposal(WINNING_PROPOSAL_ID, assignment,
                cleaner(5L, "cleaner.one@example.com"), ProposalStatus.SUBMITTED);
        CleanupProposal loser = proposal(LOSING_PROPOSAL_ID, assignment,
                cleaner(6L, "cleaner.two@example.com"), ProposalStatus.REVISION_REQUIRED);

        signInAsCorporation(corporation);
        when(proposalRepository.findById(WINNING_PROPOSAL_ID)).thenReturn(Optional.of(winner));
        when(proposalRepository.findByAssignmentOrderBySubmittedAtAsc(assignment))
                .thenReturn(List.of(winner, loser));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideProposal(WINNING_PROPOSAL_ID, request(ApprovalDecision.APPROVED, null));

        assertEquals(ProposalStatus.APPROVED, winner.getStatus());
        assertEquals(ProposalStatus.REJECTED, loser.getStatus()); // only one proposal can be authorised
    }

    @Test
    void rejectingTheLastLiveProposalReopensTheAssignment() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal only = proposal(WINNING_PROPOSAL_ID, assignment,
                cleaner(5L, "cleaner.one@example.com"), ProposalStatus.SUBMITTED);

        signInAsCorporation(corporation);
        when(proposalRepository.findById(WINNING_PROPOSAL_ID)).thenReturn(Optional.of(only));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideProposal(WINNING_PROPOSAL_ID, request(ApprovalDecision.REJECTED, "Not viable"));

        assertEquals(ProposalStatus.REJECTED, only.getStatus());
        assertEquals(AssignmentStatus.PENDING, assignment.getStatus()); // open for fresh proposals again
        assertNull(assignment.getCleaner());                           // nobody was authorised
    }

    @Test
    void requestingRevisionKeepsTheAssignmentUnderReview() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal submitted = proposal(WINNING_PROPOSAL_ID, assignment,
                cleaner(5L, "cleaner.one@example.com"), ProposalStatus.SUBMITTED);

        signInAsCorporation(corporation);
        when(proposalRepository.findById(WINNING_PROPOSAL_ID)).thenReturn(Optional.of(submitted));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideProposal(
                WINNING_PROPOSAL_ID, request(ApprovalDecision.REVISION_REQUIRED, "Add manpower details"));

        assertEquals(ProposalStatus.REVISION_REQUIRED, submitted.getStatus()); // cleaner may resubmit
        assertEquals(AssignmentStatus.PROPOSAL_SUBMITTED, assignment.getStatus());
        assertNull(assignment.getCleaner());
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class)); // lifecycle untouched
    }

    @Test
    void proposalCannotBeApprovedTwiceForTheSameAssignment() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.ASSIGNED,
                cleaner(5L, "cleaner.one@example.com"));
        CleanupProposal latecomer = proposal(LOSING_PROPOSAL_ID, assignment,
                cleaner(6L, "cleaner.two@example.com"), ProposalStatus.SUBMITTED);

        signInAsCorporation(corporation);
        when(proposalRepository.findById(LOSING_PROPOSAL_ID)).thenReturn(Optional.of(latecomer));
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        assertThrows(InvalidProposalStateException.class, () -> cleanupApprovalService.decideProposal(
                LOSING_PROPOSAL_ID, request(ApprovalDecision.APPROVED, null)));

        verify(approvalRepository, never()).save(any(CleanupApproval.class));
    }

    @Test
    void corporationCannotDecideProposalsOfAnotherCity() {
        MunicipalCorporation ownCorporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        MunicipalCorporation otherCorporation = corporation(20L, "Pune", "info@punecorporation.org");
        CleanupAssignment assignment = assignment(otherCorporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal foreign = proposal(WINNING_PROPOSAL_ID, assignment,
                cleaner(5L, "cleaner.one@example.com"), ProposalStatus.SUBMITTED);

        signInAsCorporation(ownCorporation);
        when(proposalRepository.findById(WINNING_PROPOSAL_ID)).thenReturn(Optional.of(foreign));

        assertThrows(UnauthorizedAssignmentAccessException.class, () -> cleanupApprovalService.decideProposal(
                WINNING_PROPOSAL_ID, request(ApprovalDecision.APPROVED, null)));

        assertEquals(ProposalStatus.SUBMITTED, foreign.getStatus()); // nothing was changed
        verify(approvalRepository, never()).save(any(CleanupApproval.class));
    }

    @Test
    void proposalQueueOnlyReturnsAssignmentsOfTheSignedInCorporation() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.PROPOSAL_SUBMITTED, null);

        signInAsCorporation(corporation);
        when(assignmentRepository.findByAssignedMunicipalCorporationAndStatusOrderByIdDesc(
                corporation, AssignmentStatus.PROPOSAL_SUBMITTED)).thenReturn(List.of(assignment));

        List<CleanupAssignmentResponse> queue = cleanupApprovalService.getPendingReviewAssignments();

        assertEquals(1, queue.size());
        assertEquals(ASSIGNMENT_ID, queue.get(0).getAssignmentId());
        assertEquals(AssignmentStatus.PROPOSAL_SUBMITTED.name(), queue.get(0).getAssignmentStatus());
    }

    @Test
    void accountThatIsNotARegisteredCorporationCannotOpenTheApprovalQueue() {
        // A self-registered account (even a MUNICIPAL cleaner) has no row in municipal_corporations
        signInAs("cleaner.municipal@example.com");
        when(municipalRepository.findByEmailIgnoreCase("cleaner.municipal@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedAssignmentAccessException.class,
                () -> cleanupApprovalService.getPendingReviewAssignments());

        verifyNoInteractions(assignmentRepository); // no city data is ever read for such an account
    }

    // ---------------------------------------------------------------------
    // COMPLETION STAGE
    // ---------------------------------------------------------------------

    @Test
    void approvingCompletionClosesTheReportAndReleasesTheReward() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        User cleaner = cleaner(5L, "cleaner.one@example.com");
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.AWAITING_APPROVAL, cleaner);

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        CleanupApprovalResponse response = cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, "Site verified"));

        assertEquals(AssignmentStatus.COMPLETED, assignment.getStatus());
        assertEquals(ReportStatus.RESOLVED, assignment.getReport().getStatus()); // citizen's report closed
        assertEquals(ApprovalStage.COMPLETION, response.getStage());
        assertNull(response.getProposalId()); // completion decisions are not tied to one proposal
        verify(rewardService).rewardCleaner(assignment); // reward released only after sign-off
        // The success story becomes publicly visible only now
        verify(publicFeedAnalyticsService).initializeAnalytics(assignment);
    }

    @Test
    void rejectingCompletionSendsTheCleanupBackWithoutAnyRewardOrPublicStory() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        User cleaner = cleaner(5L, "cleaner.one@example.com");
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.AWAITING_APPROVAL, cleaner);

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.REJECTED, "Waste still on site"));

        assertEquals(AssignmentStatus.REWORK_REQUIRED, assignment.getStatus()); // cleaner keeps working, then resubmits
        assertNull(assignment.getCompletedAt());
        assertEquals(ReportStatus.IN_PROGRESS, assignment.getReport().getStatus()); // report stays open
        verifyNoInteractions(rewardService);              // nothing paid out prematurely
        verifyNoInteractions(publicFeedAnalyticsService); // and no success story published
    }

    @Test
    void rejectingCompletionRetiresTheAiVerdictThatBelongedToTheRefusedPhotograph() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.AWAITING_APPROVAL,
                cleaner(5L, "cleaner.one@example.com"));

        // The verdict the AI recorded for the image the office is about to turn down
        assignment.setAiVerified(true);
        assignment.setAiConfidence(0.93);
        assignment.setAiRemarks("Footpath appears clear in the after image.");

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.REJECTED, "Waste still behind the wall"));

        /*
         * Left standing, this verdict counted the cleanup in the admin's
         * "verified cleanups" total and put a green "Verified by AI" banner on
         * the cleaner's task card right above the rework instruction.
         */
        assertEquals(false, assignment.getAiVerified());
        assertNull(assignment.getAiConfidence());

        // The note is kept on purpose: the rework card shows it as "Last AI note"
        assertEquals("Footpath appears clear in the after image.", assignment.getAiRemarks());
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void requestingReworkOnCompletionAlsoRetiresTheAiVerdict() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.AWAITING_APPROVAL,
                cleaner(5L, "cleaner.one@example.com"));
        assignment.setAiVerified(true);
        assignment.setAiConfidence(0.88);

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        // REVISION_REQUIRED at the completion stage is the "request rework" button
        cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.REVISION_REQUIRED, "Sweep the drain mouth too"));

        assertEquals(AssignmentStatus.REWORK_REQUIRED, assignment.getStatus());
        assertEquals(false, assignment.getAiVerified()); // the next upload records a fresh verdict
        assertNull(assignment.getAiConfidence());
        verifyNoInteractions(rewardService);
    }

    @Test
    void approvingCompletionLeavesTheAiVerdictOnRecord() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.AWAITING_APPROVAL,
                cleaner(5L, "cleaner.one@example.com"));
        assignment.setAiVerified(true);
        assignment.setAiConfidence(0.93);

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.save(any(CleanupApproval.class))).thenAnswer(call -> call.getArgument(0));

        cleanupApprovalService.decideCompletion(ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, "Site verified"));

        // The verdict describes the photograph that was accepted, so it stands as history
        assertEquals(true, assignment.getAiVerified());
        assertEquals(0.93, assignment.getAiConfidence());
    }

    @Test
    void aCleanupSentBackForReworkCannotBeDecidedAgainUntilFreshProofArrives() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.REWORK_REQUIRED,
                cleaner(5L, "cleaner.one@example.com"));

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        /*
         * Guards against a double decision on a stale queue card: the officer's
         * list is client-side, so a second click after the rework request must
         * not release a reward against evidence that has not been re-uploaded.
         */
        assertThrows(InvalidAssignmentStateException.class, () -> cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, "Changed my mind")));

        assertEquals(AssignmentStatus.REWORK_REQUIRED, assignment.getStatus());
        verifyNoInteractions(rewardService);
        verifyNoInteractions(publicFeedAnalyticsService);
    }

    @Test
    void anAlreadyCompletedCleanupCannotBeRewardedTwice() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.COMPLETED,
                cleaner(5L, "cleaner.one@example.com"));

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        // "A cleanup must be rewarded exactly once" - the status gate is what enforces it
        assertThrows(InvalidAssignmentStateException.class, () -> cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, "Signing off again")));

        verifyNoInteractions(rewardService);
        verifyNoInteractions(publicFeedAnalyticsService);
    }

    @Test
    void completionCannotBeDecidedBeforeProofIsSubmitted() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment assignment = assignment(corporation, AssignmentStatus.IN_PROGRESS,
                cleaner(5L, "cleaner.one@example.com"));

        signInAsCorporation(corporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(InvalidAssignmentStateException.class, () -> cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, null)));

        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.getStatus());
        verifyNoInteractions(rewardService);
        verifyNoInteractions(publicFeedAnalyticsService);
    }

    @Test
    void corporationCannotSignOffCompletionOfAnotherCity() {
        MunicipalCorporation ownCorporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        MunicipalCorporation otherCorporation = corporation(20L, "Pune", "info@punecorporation.org");
        CleanupAssignment assignment = assignment(otherCorporation, AssignmentStatus.AWAITING_APPROVAL,
                cleaner(5L, "cleaner.one@example.com"));

        signInAsCorporation(ownCorporation);
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(UnauthorizedAssignmentAccessException.class, () -> cleanupApprovalService.decideCompletion(
                ASSIGNMENT_ID, request(ApprovalDecision.APPROVED, "Looks fine")));

        assertEquals(AssignmentStatus.AWAITING_APPROVAL, assignment.getStatus()); // untouched
        verifyNoInteractions(rewardService);
        verifyNoInteractions(publicFeedAnalyticsService);
    }

    // ---------------------------------------------------------------------
    // COMPLETION HISTORY (municipal history desk)
    // ---------------------------------------------------------------------

    @Test
    void completionHistoryOnlyReturnsSignedOffCleanupsOfTheSignedInCorporation() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        CleanupAssignment closed = assignment(corporation, AssignmentStatus.COMPLETED,
                cleaner(5L, "cleaner.one@example.com"));

        signInAsCorporation(corporation);
        when(assignmentRepository.findCompletedByMunicipalCorporationNewestFirst(corporation))
                .thenReturn(List.of(closed));

        List<CleanupAssignmentResponse> history = cleanupApprovalService.getCompletedCleanups();

        assertEquals(1, history.size());
        assertEquals(ASSIGNMENT_ID, history.get(0).getAssignmentId());
        assertEquals(AssignmentStatus.COMPLETED.name(), history.get(0).getAssignmentStatus());

        // The corporation is resolved from the token, so the query can only ever
        // be issued for the officer's own city
        verify(assignmentRepository).findCompletedByMunicipalCorporationNewestFirst(corporation);
    }

    @Test
    void completionHistoryPreservesTheNewestFirstOrderTheRepositoryReturns() {
        MunicipalCorporation corporation = corporation(CORPORATION_ID, CORPORATION_CITY, CORPORATION_EMAIL);
        User cleaner = cleaner(5L, "cleaner.one@example.com");

        CleanupAssignment approvedToday = assignment(corporation, AssignmentStatus.COMPLETED, cleaner);
        approvedToday.setCompletedAt(LocalDateTime.now());

        CleanupAssignment approvedLastWeek = assignment(corporation, AssignmentStatus.COMPLETED, cleaner);
        approvedLastWeek.setId(ASSIGNMENT_ID + 1);
        approvedLastWeek.setCompletedAt(LocalDateTime.now().minusDays(7));

        signInAsCorporation(corporation);
        // Repository orders on completedAt DESC; the service must not resequence it
        when(assignmentRepository.findCompletedByMunicipalCorporationNewestFirst(corporation))
                .thenReturn(List.of(approvedToday, approvedLastWeek));

        List<CleanupAssignmentResponse> history = cleanupApprovalService.getCompletedCleanups();

        assertEquals(ASSIGNMENT_ID, history.get(0).getAssignmentId());     // latest sign-off at the top
        assertEquals(ASSIGNMENT_ID + 1, history.get(1).getAssignmentId()); // oldest at the bottom
    }

    @Test
    void accountThatIsNotARegisteredCorporationCannotOpenTheCompletionHistory() {
        signInAs("cleaner.municipal@example.com");
        when(municipalRepository.findByEmailIgnoreCase("cleaner.municipal@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(UnauthorizedAssignmentAccessException.class,
                () -> cleanupApprovalService.getCompletedCleanups());

        verifyNoInteractions(assignmentRepository); // no city data is ever read for such an account
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    /** Puts the corporation's official email in the security context, like the JWT filter does. */
    private void signInAsCorporation(MunicipalCorporation corporation) {
        signInAs(corporation.getEmail());
        when(municipalRepository.findByEmailIgnoreCase(corporation.getEmail()))
                .thenReturn(Optional.of(corporation));
    }

    private void signInAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
        SecurityContextHolder.setContext(context);
    }

    private User cleaner(long id, String email) {
        return User.builder()
                .id(id)
                .name("Cleaner " + id)
                .email(email)
                .role(Role.ROLE_CLEANER)
                .city(CORPORATION_CITY)
                .build();
    }

    private MunicipalCorporation corporation(long id, String city, String email) {
        return MunicipalCorporation.builder()
                .id(id)
                .organizationName(city + " Municipal Corporation")
                .city(city)
                .email(email) // the login identity registered by the admin
                .build();
    }

    private CleanupAssignment assignment(MunicipalCorporation corporation, AssignmentStatus status, User cleaner) {
        GarbageReport report = GarbageReport.builder()
                .id(7L)
                .title("Garbage pile near market")
                .description("Mixed waste dumped on the footpath")
                .address("Phase 7 Market")
                .city(corporation.getCity())
                .state("Punjab")
                .pincode("160055")
                .latitude(30.7046)
                .longitude(76.7179)
                .status(ReportStatus.IN_PROGRESS) // still open until the corporation signs off
                .build();

        return CleanupAssignment.builder()
                .id(ASSIGNMENT_ID)
                .report(report)
                .cleaner(cleaner)
                .assignedMunicipalCorporation(corporation)
                .status(status)
                .build();
    }

    private CleanupProposal proposal(long id, CleanupAssignment assignment, User cleaner, ProposalStatus status) {
        return CleanupProposal.builder()
                .id(id)
                .assignment(assignment)
                .cleaner(cleaner)
                .status(status)
                .build();
    }

    private ApprovalDecisionRequest request(ApprovalDecision decision, String remarks) {
        return ApprovalDecisionRequest.builder()
                .decision(decision)
                .remarks(remarks)
                .build();
    }
}