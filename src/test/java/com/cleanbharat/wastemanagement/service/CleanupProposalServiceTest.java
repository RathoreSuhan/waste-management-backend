package com.cleanbharat.wastemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.CreateProposalRequest;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.CleanerTooFarFromSiteException;
import com.cleanbharat.wastemanagement.exception.DuplicateProposalException;
import com.cleanbharat.wastemanagement.exception.InvalidProposalStateException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CleanupProposalRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * ============================================================================
 * CleanupProposalServiceTest (Phase 14)
 * ============================================================================
 *
 * Locks in the rules that make the workflow "municipal authorized":
 * submitting a proposal never awards the work, several cleaners may bid for
 * the same site, and inspection evidence must come from the site itself.
 * ============================================================================
 */
@ExtendWith(MockitoExtension.class)
class CleanupProposalServiceTest {

    private static final String CLEANER_EMAIL = "cleaner.one@example.com";
    private static final String SECOND_CLEANER_EMAIL = "cleaner.two@example.com";
    private static final double SITE_LATITUDE = 19.0760;   // reported garbage location
    private static final double SITE_LONGITUDE = 72.8777;
    private static final long ASSIGNMENT_ID = 5L;
    private static final long PROPOSAL_ID = 77L;

    @Mock
    private CleanupProposalRepository proposalRepository;

    @Mock
    private CleanupApprovalRepository approvalRepository; // the service now reads and appends decision ledger rows

    @Mock
    private CleanupAssignmentRepository assignmentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CloudinaryService cloudinaryService;

    @InjectMocks
    private CleanupProposalServiceImpl cleanupProposalService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // tests share one thread, so never leak a principal
    }

    // ------------------------------------------------------------ core rules

    @Test
    void submitProposalDoesNotAwardTheSiteToTheFirstCleaner() {
        User cleaner = cleaner(2L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PENDING, null);
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(proposalRepository.findByAssignmentAndCleaner(assignment, cleaner)).thenReturn(Optional.empty());
        when(proposalRepository.save(any(CleanupProposal.class))).thenAnswer(invocation -> {
            CleanupProposal saved = invocation.getArgument(0);
            saved.setId(PROPOSAL_ID); // stand in for the database identity
            return saved;
        });
        when(proposalRepository.countByAssignment(assignment)).thenReturn(1L);

        CleanupProposalResponse response = cleanupProposalService.submitProposal(
                ASSIGNMENT_ID, request(SITE_LATITUDE, SITE_LONGITUDE));

        assertEquals(PROPOSAL_ID, response.getProposalId());
        assertEquals(ProposalStatus.SUBMITTED.name(), response.getStatus()); // waits for municipal review
        assertNull(assignment.getCleaner()); // the crucial rule: no automatic award
        assertEquals(AssignmentStatus.PROPOSAL_SUBMITTED, assignment.getStatus()); // only flagged as "has bids"
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void submitProposalAllowsASecondCleanerOnTheSameSite() {
        User secondCleaner = cleaner(3L, SECOND_CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PROPOSAL_SUBMITTED, null); // already has one bid
        authenticateAs(SECOND_CLEANER_EMAIL);

        when(userRepository.findByEmail(SECOND_CLEANER_EMAIL)).thenReturn(Optional.of(secondCleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(proposalRepository.findByAssignmentAndCleaner(assignment, secondCleaner)).thenReturn(Optional.empty());
        when(proposalRepository.save(any(CleanupProposal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(proposalRepository.countByAssignment(assignment)).thenReturn(2L);

        CleanupProposalResponse response = cleanupProposalService.submitProposal(
                ASSIGNMENT_ID, request(SITE_LATITUDE, SITE_LONGITUDE));

        assertEquals(2L, response.getTotalProposalsForAssignment()); // competition is visible to the cleaner
        assertEquals(3L, response.getCleanerId());
        assertNull(assignment.getCleaner()); // still unawarded after the second proposal
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class)); // status already PROPOSAL_SUBMITTED
    }

    @Test
    void submitProposalRejectsASecondProposalFromTheSameCleaner() {
        User cleaner = cleaner(2L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PROPOSAL_SUBMITTED, null);
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(proposalRepository.findByAssignmentAndCleaner(assignment, cleaner))
                .thenReturn(Optional.of(proposal(assignment, cleaner, ProposalStatus.SUBMITTED)));

        assertThrows(DuplicateProposalException.class, () -> cleanupProposalService.submitProposal(
                ASSIGNMENT_ID, request(SITE_LATITUDE, SITE_LONGITUDE)));

        verify(proposalRepository, never()).save(any(CleanupProposal.class));
    }

    @Test
    void submitProposalRejectsAnAlreadyAwardedSite() {
        User cleaner = cleaner(2L, CLEANER_EMAIL);
        User awardedCleaner = cleaner(9L, "awarded@example.com");
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, awardedCleaner); // municipality decided
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(InvalidProposalStateException.class, () -> cleanupProposalService.submitProposal(
                ASSIGNMENT_ID, request(SITE_LATITUDE, SITE_LONGITUDE)));

        verify(proposalRepository, never()).save(any(CleanupProposal.class));
    }

    @Test
    void submitProposalRejectsInspectionFromOutsideTheAllowedRadius() {
        User cleaner = cleaner(2L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PENDING, null);
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(proposalRepository.findByAssignmentAndCleaner(assignment, cleaner)).thenReturn(Optional.empty());

        CleanerTooFarFromSiteException exception = assertThrows(CleanerTooFarFromSiteException.class,
                () -> cleanupProposalService.submitProposal(
                        ASSIGNMENT_ID,
                        request(SITE_LATITUDE + 0.01, SITE_LONGITUDE))); // roughly 1.1 km away

        assertTrue(exception.getMessage().contains("away from the site"));
        verify(proposalRepository, never()).save(any(CleanupProposal.class));
        verify(cloudinaryService, never()).uploadFile(any()); // no upload before the location passes
    }

    @Test
    void submitProposalRejectsNonCleanerAccounts() {
        User citizen = User.builder()
                .id(4L)
                .name("Citizen")
                .email(CLEANER_EMAIL)
                .role(Role.ROLE_CITIZEN) // reporting role, not a cleaning agency
                .build();
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(citizen));

        assertThrows(UnauthorizedAssignmentAccessException.class, () -> cleanupProposalService.submitProposal(
                ASSIGNMENT_ID, request(SITE_LATITUDE, SITE_LONGITUDE)));

        verify(assignmentRepository, never()).findById(any()); // guard runs before anything is loaded
    }

    // ------------------------------------------------------- lifecycle rules

    @Test
    void withdrawProposalKeepsTheRowForAudit() {
        User cleaner = cleaner(2L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PROPOSAL_SUBMITTED, null);
        CleanupProposal existing = proposal(assignment, cleaner, ProposalStatus.SUBMITTED);
        authenticateAs(CLEANER_EMAIL);

        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(proposalRepository.findById(PROPOSAL_ID)).thenReturn(Optional.of(existing));
        when(proposalRepository.save(existing)).thenReturn(existing);
        when(proposalRepository.countByAssignment(assignment)).thenReturn(1L);

        CleanupProposalResponse response = cleanupProposalService.withdrawProposal(PROPOSAL_ID);

        assertEquals(ProposalStatus.WITHDRAWN.name(), response.getStatus());
        verify(proposalRepository, never()).delete(any(CleanupProposal.class)); // withdrawn, never deleted
    }

    @Test
    void getProposalHidesOtherCleanersProposals() {
        User owner = cleaner(2L, CLEANER_EMAIL);
        User intruder = cleaner(3L, SECOND_CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.PROPOSAL_SUBMITTED, null);
        authenticateAs(SECOND_CLEANER_EMAIL);

        when(userRepository.findByEmail(SECOND_CLEANER_EMAIL)).thenReturn(Optional.of(intruder));
        when(proposalRepository.findById(PROPOSAL_ID))
                .thenReturn(Optional.of(proposal(assignment, owner, ProposalStatus.SUBMITTED)));

        assertThrows(UnauthorizedAssignmentAccessException.class,
                () -> cleanupProposalService.getProposal(PROPOSAL_ID));
    }

    // ----------------------------------------------------------- test helpers

    // Puts a JWT-style principal in the context, which is all the service reads
    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
        SecurityContextHolder.setContext(context);
    }

    // A cleaner registered for the same city and state as the report
    private User cleaner(Long id, String email) {
        return User.builder()
                .id(id)
                .name("Cleaner " + id)
                .email(email)
                .role(Role.ROLE_CLEANER)
                .state("Maharashtra")
                .city("Mumbai")
                .build();
    }

    private GarbageReport report() {
        return GarbageReport.builder()
                .id(10L)
                .title("Garbage pile near the market")
                .address("Market Road")
                .city("Mumbai")
                .state("Maharashtra")
                .latitude(SITE_LATITUDE)
                .longitude(SITE_LONGITUDE)
                .build();
    }

    private CleanupAssignment assignment(AssignmentStatus status, User awardedCleaner) {
        return CleanupAssignment.builder()
                .id(ASSIGNMENT_ID)
                .report(report())
                .cleaner(awardedCleaner) // null while the site is still open for proposals
                .status(status)
                .build();
    }

    private CleanupProposal proposal(CleanupAssignment assignment, User cleaner, ProposalStatus status) {
        return CleanupProposal.builder()
                .id(PROPOSAL_ID)
                .assignment(assignment)
                .cleaner(cleaner)
                .inspectionLatitude(SITE_LATITUDE)
                .inspectionLongitude(SITE_LONGITUDE)
                .status(status)
                .build();
    }

    // A complete, valid execution plan; individual tests only vary the coordinates
    private CreateProposalRequest request(double latitude, double longitude) {
        return CreateProposalRequest.builder()
                .inspectionLatitude(latitude)
                .inspectionLongitude(longitude)
                .siteObservations("Mixed household waste spread across the footpath and drain mouth.")
                .estimatedDurationDays(2)
                .manpowerCount(4)
                .equipment("Two handcarts, brooms, shovels, gloves and safety masks")
                .cleaningMethod("Manual segregation on site, then transport to the transfer station")
                .wasteHandlingPlan("Dry waste to the empanelled recycler, wet waste to the ward compost yard.")
                .estimatedWasteVolume("About 3 cubic metres")
                .proposedStartDate(LocalDate.now().plusDays(1))
                .remarks("Traffic is lighter before 9 am, so work will start early.")
                .build();
    }
}