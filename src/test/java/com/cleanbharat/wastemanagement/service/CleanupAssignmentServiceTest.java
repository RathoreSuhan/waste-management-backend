package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.ai.AICleanupVerificationResponse;
import com.cleanbharat.wastemanagement.dto.ai.CleanupValidationResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.AssignmentAlreadyCompletedException;
import com.cleanbharat.wastemanagement.exception.CleanerTooFarFromSiteException;
import com.cleanbharat.wastemanagement.exception.CleanupNotStartedException;
import com.cleanbharat.wastemanagement.exception.InvalidAssignmentStateException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AICleanupVerificationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cleaner half of the municipal-authorized cleanup workflow.
 *
 * Requirements covered here:
 * - a cleaner cannot start work before the Municipal Corporation approved the proposal
 * - a rejected / not-yet-awarded site cannot be started at all
 * - only the awarded cleaner may act on an assignment
 * - GPS proof (50 m rule) is enforced when starting work and when uploading proof
 * - AI-verified proof only reaches AWAITING_APPROVAL: no reward, no resolved report,
 *   no public feed entry is produced by this service
 */
@ExtendWith(MockitoExtension.class)
class CleanupAssignmentServiceTest {

    private static final String CLEANER_EMAIL = "cleaner.one@example.com";
    private static final String OTHER_CLEANER_EMAIL = "cleaner.two@example.com";
    private static final String CITIZEN_EMAIL = "citizen.nine@example.com";
    private static final long ASSIGNMENT_ID = 41L;

    // Site coordinates used by the fixture report (Mumbai)
    private static final double SITE_LATITUDE = 19.0760;
    private static final double SITE_LONGITUDE = 72.8777;

    // Roughly 2.7 km north of the site: well outside the 50 m proof radius
    private static final double FAR_LATITUDE = 19.1000;
    private static final double FAR_LONGITUDE = 72.8777;

    private static final String BEFORE_IMAGE_URL = "https://cloudinary.test/before.jpg";
    private static final String AFTER_IMAGE_URL = "https://cloudinary.test/after.jpg";

    @Mock private CleanupAssignmentRepository assignmentRepository;
    @Mock private CleanupApprovalRepository approvalRepository;
    @Mock private MunicipalCorporationRepository municipalRepository;
    @Mock private UserRepository userRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private AICleanupVerificationService aiCleanupVerificationService;

    @InjectMocks private CleanupAssignmentServiceImpl cleanupAssignmentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // tests share one thread, so never leak a principal
    }

    // ---------------------------------------------------------------------
    // STARTING WORK: MUNICIPAL AUTHORISATION
    // ---------------------------------------------------------------------

    @Test
    void cleanerCannotStartWorkBeforeTheMunicipalityApprovesTheProposal() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        // No APPROVED decision on record for the PROPOSAL stage
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(false);

        assertThrows(InvalidAssignmentStateException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE));

        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus()); // untouched
        assertNull(assignment.getStartedAt());
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class));
    }

    @Test
    void cleanerCanStartWorkOnceTheMunicipalityHasApprovedTheProposal() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        // Municipal officer approved this cleaner's proposal
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE);

        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.getStatus());
        assertNotNull(assignment.getStartedAt());
        assertEquals(SITE_LATITUDE, assignment.getStartLatitude());   // start evidence stored
        assertEquals(SITE_LONGITUDE, assignment.getStartLongitude());
        assertTrue(assignment.getStartDistanceMeters() < 1.0);        // standing on the reported spot
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void startingWorkMovesTheCitizensReportToInProgress() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);
        assignment.getReport().setStatus(ReportStatus.PENDING); // citizen still reads "Pending" before boots are on site

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE);

        // The report must travel with the work, or the citizen / public register / dashboard all keep saying PENDING
        assertEquals(ReportStatus.IN_PROGRESS, assignment.getReport().getStatus());
    }

    @Test
    void restartingWorkNeverReopensAnAlreadyResolvedReport() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);
        assignment.getReport().setStatus(ReportStatus.RESOLVED); // municipality has already signed this site off

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE);

        assertEquals(ReportStatus.RESOLVED, assignment.getReport().getStatus()); // only PENDING moves forward
    }

    @Test
    void aSiteNotYetAwardedToAnyoneCannotBeStarted() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        // Proposal submitted but rejected / still undecided: nobody has been awarded the work
        CleanupAssignment assignment = assignment(AssignmentStatus.PROPOSAL_SUBMITTED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(InvalidAssignmentStateException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE));

        verifyNoInteractions(approvalRepository); // state is refused before approvals are consulted
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class));
    }

    @Test
    void anotherCleanerCannotStartSomeoneElsesApprovedCleanup() {
        User awardedCleaner = cleaner(5L, CLEANER_EMAIL);
        User intruder = cleaner(6L, OTHER_CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, awardedCleaner);

        authenticateAs(OTHER_CLEANER_EMAIL);
        when(userRepository.findByEmail(OTHER_CLEANER_EMAIL)).thenReturn(Optional.of(intruder));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(UnauthorizedAssignmentAccessException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE));

        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus());
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class));
    }

    @Test
    void aCitizenCannotStartACleanup() {
        User citizen = User.builder()
                .id(9L)
                .name("Citizen Nine")
                .email(CITIZEN_EMAIL)
                .role(Role.ROLE_CITIZEN) // wrong role for the cleanup workflow
                .state("Maharashtra")
                .city("Mumbai")
                .build();

        authenticateAs(CITIZEN_EMAIL);
        when(userRepository.findByEmail(CITIZEN_EMAIL)).thenReturn(Optional.of(citizen));

        assertThrows(UnauthorizedAssignmentAccessException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, SITE_LATITUDE, SITE_LONGITUDE));

        verifyNoInteractions(assignmentRepository); // role is checked before anything is loaded
    }

    // ---------------------------------------------------------------------
    // STARTING WORK: GPS EVIDENCE
    // ---------------------------------------------------------------------

    @Test
    void startingWorkIsRefusedFromOutsideTheSiteRadius() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        assertThrows(CleanerTooFarFromSiteException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, FAR_LATITUDE, FAR_LONGITUDE));

        assertEquals(AssignmentStatus.ASSIGNED, assignment.getStatus()); // approval alone is not enough
        verify(assignmentRepository, never()).save(any(CleanupAssignment.class));
    }

    @Test
    void startingWorkIsRefusedWhenTheDeviceReportsNoLocation() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(approvalRepository.existsByAssignmentAndStageAndDecision(
                assignment, ApprovalStage.PROPOSAL, ApprovalDecision.APPROVED)).thenReturn(true);

        // Missing coordinates cannot be treated as "close enough": the check may not be skipped
        assertThrows(CleanerTooFarFromSiteException.class,
                () -> cleanupAssignmentService.startCleanup(ASSIGNMENT_ID, null, null));

        verify(assignmentRepository, never()).save(any(CleanupAssignment.class));
    }

    // ---------------------------------------------------------------------
    // CLEANUP PROOF: GPS + AI, THEN MUNICIPAL QUEUE
    // ---------------------------------------------------------------------

    @Test
    void aiVerifiedProofOnlyQueuesTheCleanupForMunicipalApproval() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        MultipartFile proof = proofImage();

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(cloudinaryService.uploadFile(proof)).thenReturn(AFTER_IMAGE_URL);
        when(aiCleanupVerificationService.validateImages(BEFORE_IMAGE_URL, AFTER_IMAGE_URL))
                .thenReturn(aiVerdict(true, true, 0.93)); // same place, garbage gone, confident

        CleanupValidationResponse response = cleanupAssignmentService.uploadCleanupImage(
                ASSIGNMENT_ID, proof, SITE_LATITUDE, SITE_LONGITUDE);

        assertTrue(response.getAiVerified());
        assertEquals(AssignmentStatus.AWAITING_APPROVAL.name(), response.getAssignmentStatus());
        assertEquals(AssignmentStatus.AWAITING_APPROVAL, assignment.getStatus());
        assertEquals(AFTER_IMAGE_URL, assignment.getCleanupImageUrl());

        // The municipality has the final word: nothing is resolved or paid out here
        assertEquals(ReportStatus.IN_PROGRESS, assignment.getReport().getStatus());
        assertEquals(ReportStatus.IN_PROGRESS.name(), response.getReportStatus());
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void proofRejectedByTheAiKeepsTheCleanerOnSite() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        MultipartFile proof = proofImage();

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(cloudinaryService.uploadFile(proof)).thenReturn(AFTER_IMAGE_URL);
        when(aiCleanupVerificationService.validateImages(BEFORE_IMAGE_URL, AFTER_IMAGE_URL))
                .thenReturn(aiVerdict(true, false, 0.90)); // garbage still visible

        CleanupValidationResponse response = cleanupAssignmentService.uploadCleanupImage(
                ASSIGNMENT_ID, proof, SITE_LATITUDE, SITE_LONGITUDE);

        assertFalse(response.getAiVerified());
        assertEquals(AssignmentStatus.IN_PROGRESS, assignment.getStatus()); // may upload again
        verify(assignmentRepository).save(assignment);
    }

    @Test
    void aMunicipalReworkRequestStaysAReDoWhenTheAiRejectsTheNewProof() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        // Municipality sent the work back, so the cleaner is re-doing it
        CleanupAssignment assignment = assignment(AssignmentStatus.REWORK_REQUIRED, cleaner);
        MultipartFile proof = proofImage();

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(cloudinaryService.uploadFile(proof)).thenReturn(AFTER_IMAGE_URL);
        when(aiCleanupVerificationService.validateImages(BEFORE_IMAGE_URL, AFTER_IMAGE_URL))
                .thenReturn(aiVerdict(false, true, 0.40)); // photographed somewhere else

        cleanupAssignmentService.uploadCleanupImage(ASSIGNMENT_ID, proof, SITE_LATITUDE, SITE_LONGITUDE);

        // The rework instruction must not disappear from the cleaner's task list
        assertEquals(AssignmentStatus.REWORK_REQUIRED, assignment.getStatus());
    }

    @Test
    void proofCannotBeUploadedBeforeTheCleanupIsStarted() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        // Approved by the municipality, but START CLEANUP was never pressed
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(CleanupNotStartedException.class, () -> cleanupAssignmentService.uploadCleanupImage(
                ASSIGNMENT_ID, proofImage(), SITE_LATITUDE, SITE_LONGITUDE));

        verifyNoInteractions(cloudinaryService, aiCleanupVerificationService);
    }

    @Test
    void proofCannotBeUploadedOnceTheMunicipalityCompletedTheCleanup() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.COMPLETED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(AssignmentAlreadyCompletedException.class, () -> cleanupAssignmentService.uploadCleanupImage(
                ASSIGNMENT_ID, proofImage(), SITE_LATITUDE, SITE_LONGITUDE));

        verifyNoInteractions(cloudinaryService, aiCleanupVerificationService);
    }

    @Test
    void proofUploadIsRefusedFromOutsideTheSiteRadius() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(CleanerTooFarFromSiteException.class, () -> cleanupAssignmentService.uploadCleanupImage(
                ASSIGNMENT_ID, proofImage(), FAR_LATITUDE, FAR_LONGITUDE));

        // Position is checked first, so a photo taken away from the site never reaches Cloudinary or the AI
        verifyNoInteractions(cloudinaryService, aiCleanupVerificationService);
    }

    @Test
    void replacingARejectedProofImageReleasesTheOldCloudinaryFile() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        assignment.setCleanupImageUrl("https://cloudinary.test/rejected-attempt.jpg"); // earlier rejected upload
        MultipartFile proof = proofImage();

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(cloudinaryService.uploadFile(proof)).thenReturn(AFTER_IMAGE_URL);
        when(aiCleanupVerificationService.validateImages(BEFORE_IMAGE_URL, AFTER_IMAGE_URL))
                .thenReturn(aiVerdict(true, true, 0.91));

        cleanupAssignmentService.uploadCleanupImage(ASSIGNMENT_ID, proof, SITE_LATITUDE, SITE_LONGITUDE);

        verify(cloudinaryService).deleteFile("https://cloudinary.test/rejected-attempt.jpg"); // no orphan assets
        assertEquals(AFTER_IMAGE_URL, assignment.getCleanupImageUrl());
    }

    // ---------------------------------------------------------------------
    // CLEANER DASHBOARD LISTS
    // ---------------------------------------------------------------------

    @Test
    void cleanerSeesOnlyTheirOwnTasks() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findByCleaner(cleaner)).thenReturn(List.of(assignment));

        List<CleanupAssignmentResponse> tasks = cleanupAssignmentService.getMyTasks();

        assertEquals(1, tasks.size());
        assertEquals(ASSIGNMENT_ID, tasks.get(0).getAssignmentId());
        assertEquals("Cleaner 5", tasks.get(0).getCleanerName());
        assertEquals("Brihanmumbai Municipal Corporation", tasks.get(0).getMunicipalCorporation());
    }

    @Test
    void openSitesFromAnotherCityAreNotOfferedToTheCleaner() {
        User outOfTownCleaner = User.builder()
                .id(7L)
                .name("Cleaner 7")
                .email(CLEANER_EMAIL)
                .role(Role.ROLE_CLEANER)
                .state("Punjab")   // works in a different municipality
                .city("Mohali")
                .build();
        CleanupAssignment mumbaiSite = assignment(AssignmentStatus.PENDING, null);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(outOfTownCleaner));
        when(assignmentRepository.findByCleanerIsNullAndStatusInOrderByIdDesc(
                List.of(AssignmentStatus.PENDING, AssignmentStatus.PROPOSAL_SUBMITTED)))
                .thenReturn(List.of(mumbaiSite));

        List<CleanupAssignmentResponse> open = cleanupAssignmentService.getPendingAssignments();

        assertTrue(open.isEmpty()); // city-scoped work stays with the city's own cleaners
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    private void authenticateAs(String email) {
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
                .state("Maharashtra")
                .city("Mumbai")
                .build();
    }

    private CleanupAssignment assignment(AssignmentStatus status, User cleaner) {
        GarbageReport report = GarbageReport.builder()
                .id(7L)
                .title("Garbage pile near market")
                .description("Mixed waste dumped on the footpath")
                .imageUrl(BEFORE_IMAGE_URL) // citizen's "before" photo the AI compares against
                .address("MG Road")
                .city("Mumbai")
                .state("Maharashtra")
                .pincode("400001")
                .latitude(SITE_LATITUDE)
                .longitude(SITE_LONGITUDE)
                .status(ReportStatus.IN_PROGRESS) // open until the municipality signs off
                .build();

        return CleanupAssignment.builder()
                .id(ASSIGNMENT_ID)
                .report(report)
                .cleaner(cleaner)
                .assignedMunicipalCorporation(corporation())
                .status(status)
                .build();
    }

    private MunicipalCorporation corporation() {
        return MunicipalCorporation.builder()
                .id(3L)
                .city("Mumbai")
                .organizationName("Brihanmumbai Municipal Corporation")
                .phone("022 22620149")
                .email("mcgm.swmproject@example.com")
                .build();
    }

    private MultipartFile proofImage() {
        return new MockMultipartFile("image", "after.jpg", "image/jpeg", "binary-content".getBytes());
    }

    private AICleanupVerificationResponse aiVerdict(boolean sameLocation, boolean garbageRemoved, double confidence) {
        return AICleanupVerificationResponse.builder()
                .sameLocation(sameLocation)
                .garbageRemoved(garbageRemoved)
                .confidence(confidence)
                .remarks("Automated comparison of the before and after photographs")
                .build();
    }
}