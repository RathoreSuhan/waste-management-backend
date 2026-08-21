package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupActivityLogRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.entity.CleanupActivityLog;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidAssignmentStateException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * Unit tests for the optional cleanup work diary.
 * Focus: only the authorized cleaner may write entries, entries are only accepted
 * while the cleanup is IN_PROGRESS, and every piece of evidence stays optional.
 */
@ExtendWith(MockitoExtension.class)
class CleanupActivityLogServiceTest {

    private static final String CLEANER_EMAIL = "cleaner.one@example.com";
    private static final String OTHER_CLEANER_EMAIL = "cleaner.two@example.com";
    private static final long ASSIGNMENT_ID = 41L;
    private static final long ACTIVITY_LOG_ID = 71L;

    // Site coordinates used by the fixture report (Mumbai)
    private static final double SITE_LATITUDE = 19.0760;
    private static final double SITE_LONGITUDE = 72.8777;

    @Mock private CleanupActivityLogRepository activityLogRepository;
    @Mock private CleanupAssignmentRepository assignmentRepository;
    @Mock private UserRepository userRepository;
    @Mock private CloudinaryService cloudinaryService;

    @InjectMocks private CleanupActivityLogServiceImpl cleanupActivityLogService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // tests share one thread, so never leak a principal
    }

    // ---------------------------------------------------------------------
    // WRITING ENTRIES
    // ---------------------------------------------------------------------

    @Test
    void cleanerCanLogATextOnlyEntryWithoutPhotoOrCoordinates() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(activityLogRepository.save(any(CleanupActivityLog.class))).thenAnswer(call -> call.getArgument(0));

        CleanupActivityLogResponse response = cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID, request("Cleared the northern half of the dump today", null, null), null);

        assertEquals("Cleared the northern half of the dump today", response.getDescription());
        assertNotNull(response.getActivityAt());          // service stamped "now" for the cleaner
        assertNull(response.getImageUrl());               // photo is optional (requirement 7)
        assertNull(response.getLatitude());               // coordinates are optional too
        assertNull(response.getDistanceMeters());         // nothing to measure without a GPS fix
        assertEquals(ASSIGNMENT_ID, response.getAssignmentId());
        verifyNoInteractions(cloudinaryService);          // no upload attempted for a text-only entry
    }

    @Test
    void cleanerCanLogAnEntryWithPhotoAndCoordinates() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        MultipartFile photo = new MockMultipartFile(
                "image", "day-two.jpg", "image/jpeg", "binary-content".getBytes());

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(cloudinaryService.uploadFile(photo)).thenReturn("https://cloudinary.test/day-two.jpg");
        when(activityLogRepository.save(any(CleanupActivityLog.class))).thenAnswer(call -> call.getArgument(0));

        CleanupActivityLogResponse response = cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID,
                request("Day two: hauled away six sacks", SITE_LATITUDE, SITE_LONGITUDE),
                photo);

        assertEquals("https://cloudinary.test/day-two.jpg", response.getImageUrl());
        assertEquals(SITE_LATITUDE, response.getLatitude());
        assertEquals(SITE_LONGITUDE, response.getLongitude());
        assertNotNull(response.getDistanceMeters());                 // recorded for the audit trail
        assertTrue(response.getDistanceMeters() < 1.0);              // standing on the reported spot
        verify(cloudinaryService).uploadFile(photo);
    }

    @Test
    void aBackDatedEntryKeepsTheDateTheCleanerSupplied() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        LocalDateTime yesterdayShift = LocalDateTime.now().minusDays(1);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(activityLogRepository.save(any(CleanupActivityLog.class))).thenAnswer(call -> call.getArgument(0));

        CleanupActivityLogRequest request = CleanupActivityLogRequest.builder()
                .description("Yesterday's evening shift, logged late")
                .activityAt(yesterdayShift) // multi-day cleanups get written up after the fact
                .build();

        CleanupActivityLogResponse response =
                cleanupActivityLogService.addActivityLog(ASSIGNMENT_ID, request, null);

        assertEquals(yesterdayShift, response.getActivityAt());
    }

    // ---------------------------------------------------------------------
    // AUTHORISATION AND STATE GUARDS
    // ---------------------------------------------------------------------

    @Test
    void anotherCleanerCannotWriteInSomeoneElsesDiary() {
        User authorisedCleaner = cleaner(5L, CLEANER_EMAIL);
        User intruder = cleaner(6L, OTHER_CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, authorisedCleaner);

        authenticateAs(OTHER_CLEANER_EMAIL);
        when(userRepository.findByEmail(OTHER_CLEANER_EMAIL)).thenReturn(Optional.of(intruder));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(UnauthorizedAssignmentAccessException.class, () -> cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID, request("Trying to claim someone else's work", null, null), null));

        verify(activityLogRepository, never()).save(any(CleanupActivityLog.class));
        verifyNoInteractions(cloudinaryService); // nothing is uploaded before ownership is proven
    }

    @Test
    void aCitizenCannotWriteACleanupDiary() {
        User citizen = User.builder()
                .id(9L)
                .name("Citizen Nine")
                .email("citizen.nine@example.com")
                .role(Role.ROLE_CITIZEN) // wrong role for the cleanup workflow
                .build();

        authenticateAs("citizen.nine@example.com");
        when(userRepository.findByEmail("citizen.nine@example.com")).thenReturn(Optional.of(citizen));

        assertThrows(UnauthorizedAssignmentAccessException.class, () -> cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID, request("Not my job", null, null), null));

        verifyNoInteractions(assignmentRepository); // role is checked before anything is loaded
    }

    @Test
    void entriesAreRejectedBeforeTheCleanupHasStarted() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        // Municipality approved the proposal, but the cleaner has not pressed START CLEANUP yet
        CleanupAssignment assignment = assignment(AssignmentStatus.ASSIGNED, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(InvalidAssignmentStateException.class, () -> cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID, request("Logging work I have not started", null, null), null));

        verify(activityLogRepository, never()).save(any(CleanupActivityLog.class));
    }

    @Test
    void entriesAreRejectedOnceProofIsUnderMunicipalReview() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        // Proof already submitted: the diary freezes so the record cannot be edited mid-review
        CleanupAssignment assignment = assignment(AssignmentStatus.AWAITING_APPROVAL, cleaner);

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));

        assertThrows(InvalidAssignmentStateException.class, () -> cleanupActivityLogService.addActivityLog(
                ASSIGNMENT_ID, request("Sneaking in an extra entry", null, null), null));

        verify(activityLogRepository, never()).save(any(CleanupActivityLog.class));
    }

    // ---------------------------------------------------------------------
    // READING AND DELETING
    // ---------------------------------------------------------------------

    @Test
    void diaryStaysReadableAfterTheWorkIsSubmitted() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.AWAITING_APPROVAL, cleaner);
        CleanupActivityLog entry = activityLog(assignment, cleaner, "Final sweep done");

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(assignmentRepository.findById(ASSIGNMENT_ID)).thenReturn(Optional.of(assignment));
        when(activityLogRepository.findByAssignmentOrderByActivityAtAsc(assignment)).thenReturn(List.of(entry));

        List<CleanupActivityLogResponse> logs = cleanupActivityLogService.getActivityLogs(ASSIGNMENT_ID);

        assertEquals(1, logs.size());                            // reading never needs IN_PROGRESS
        assertEquals("Final sweep done", logs.get(0).getDescription());
        assertEquals("Cleaner 5", logs.get(0).getCleanerName());
    }

    @Test
    void deletingAnEntryAlsoReleasesItsCloudinaryImage() {
        User cleaner = cleaner(5L, CLEANER_EMAIL);
        CleanupAssignment assignment = assignment(AssignmentStatus.IN_PROGRESS, cleaner);
        CleanupActivityLog entry = activityLog(assignment, cleaner, "Wrong photo attached");
        entry.setImageUrl("https://cloudinary.test/wrong-photo.jpg");

        authenticateAs(CLEANER_EMAIL);
        when(userRepository.findByEmail(CLEANER_EMAIL)).thenReturn(Optional.of(cleaner));
        when(activityLogRepository.findByIdAndCleaner(ACTIVITY_LOG_ID, cleaner)).thenReturn(Optional.of(entry));

        cleanupActivityLogService.deleteActivityLog(ACTIVITY_LOG_ID);

        verify(cloudinaryService).deleteFile("https://cloudinary.test/wrong-photo.jpg"); // no orphan assets
        verify(activityLogRepository).delete(entry);
    }

    @Test
    void aCleanerCannotDeleteAnEntryThatIsNotTheirs() {
        User intruder = cleaner(6L, OTHER_CLEANER_EMAIL);

        authenticateAs(OTHER_CLEANER_EMAIL);
        when(userRepository.findByEmail(OTHER_CLEANER_EMAIL)).thenReturn(Optional.of(intruder));
        // Ownership is part of the lookup, so someone else's entry simply does not exist for them
        when(activityLogRepository.findByIdAndCleaner(ACTIVITY_LOG_ID, intruder)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> cleanupActivityLogService.deleteActivityLog(ACTIVITY_LOG_ID));

        verify(activityLogRepository, never()).delete(any(CleanupActivityLog.class));
        verifyNoInteractions(cloudinaryService);
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
                .city("Mumbai")
                .build();
    }

    private CleanupAssignment assignment(AssignmentStatus status, User cleaner) {
        GarbageReport report = GarbageReport.builder()
                .id(7L)
                .title("Garbage pile near market")
                .description("Mixed waste dumped on the footpath")
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
                .status(status)
                .build();
    }

    private CleanupActivityLog activityLog(CleanupAssignment assignment, User cleaner, String description) {
        return CleanupActivityLog.builder()
                .id(ACTIVITY_LOG_ID)
                .assignment(assignment)
                .cleaner(cleaner)
                .description(description)
                .activityAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private CleanupActivityLogRequest request(String description, Double latitude, Double longitude) {
        return CleanupActivityLogRequest.builder()
                .description(description)
                .latitude(latitude)
                .longitude(longitude)
                .build();
    }
}