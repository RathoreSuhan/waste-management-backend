package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AIReportValidationService;
import com.cleanbharat.wastemanagement.service.location.ReportDuplicateValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the report-creation path that decides where a report goes.
 *
 * The report row is saved before the municipal destination is resolved, both
 * inside one service-level transaction (the class-level @Transactional on the
 * assignment service joins createReport's). These tests pin what the citizen
 * sees when that destination does not exist: a clear error naming the city,
 * no response ever mapped, and the uploaded image removed from Cloudinary -
 * the database rollback itself is Spring's, driven by the exception crossing
 * the transactional proxy.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final String CITIZEN_EMAIL = "citizen.routing@example.com";
    private static final String IMAGE_URL = "https://cloudinary.test/report.jpg";

    @Mock private GarbageReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private CleanupAssignmentService cleanupAssignmentService;
    @Mock private ReportMapper reportMapper;
    @Mock private AIReportValidationService aiReportValidationService;
    @Mock private ReportDuplicateValidationService reportDuplicateValidationService;

    @InjectMocks private ReportServiceImpl reportService;

    private final MultipartFile image = new MockMultipartFile(
            "image", "pile.jpg", "image/jpeg", "binary-content".getBytes());

    @BeforeEach
    void authenticateCitizen() {
        User citizen = User.builder()
                .id(2L)
                .name("Citizen Two")
                .email(CITIZEN_EMAIL)
                .role(Role.ROLE_CITIZEN)
                .state("Punjab")
                .city("S.a.s.nagar - Mohali")
                .build();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(CITIZEN_EMAIL, "n/a", List.of()));
        SecurityContextHolder.setContext(context);
        when(userRepository.findByEmail(CITIZEN_EMAIL)).thenReturn(Optional.of(citizen));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // tests share one thread, so never leak a principal
    }

    @Test
    void theReportIsSavedBeforeItsMunicipalDestinationIsResolved() {
        // Canonical Mohali city value, exactly as registration under ULB
        // 248325 stores it
        CreateReportRequest request = mohaliRequest();
        GarbageReport saved = GarbageReport.builder().id(21L).city(request.getCity()).build();

        when(aiReportValidationService.validateReportImage(image)).thenReturn(
                AIReportValidationResponse.builder()
                        .garbageDetected(true)
                        .validReportImage(true)
                        .garbageCategory("Mixed Garbage")
                        .build());
        when(cloudinaryService.uploadFile(image)).thenReturn(IMAGE_URL);
        when(reportRepository.save(any(GarbageReport.class))).thenReturn(saved);

        reportService.createReport(request, image);

        // Resolution reads the saved row, so this order matters - and the
        // transaction spans both steps, which is what makes a later failure
        // roll the saved row back
        InOrder pipeline = inOrder(reportRepository, cleanupAssignmentService);
        pipeline.verify(reportRepository).save(any(GarbageReport.class));
        pipeline.verify(cleanupAssignmentService).createDefaultAssignment(saved);
    }

    @Test
    void aReportWithoutAMunicipalDestinationIsRefusedAndTheUploadIsRemoved() {
        // A ULB selectable in the directory can still have no Clean Bharat
        // MunicipalCorporation row. The failure must name the city, reach no
        // response mapping, and the already-uploaded image must be cleaned up.
        CreateReportRequest request = mohaliRequest();
        when(aiReportValidationService.validateReportImage(image)).thenReturn(
                AIReportValidationResponse.builder()
                        .garbageDetected(true)
                        .validReportImage(true)
                        .garbageCategory("Mixed Garbage")
                        .build());
        when(cloudinaryService.uploadFile(image)).thenReturn(IMAGE_URL);
        when(reportRepository.save(any(GarbageReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new ResourceNotFoundException(
                "No Municipal Corporation found for city: S.a.s.nagar - Mohali"))
                .when(cleanupAssignmentService)
                .createDefaultAssignment(any(GarbageReport.class));

        ResourceNotFoundException refused = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.createReport(request, image));

        assertTrue(refused.getMessage().contains("S.a.s.nagar - Mohali"), refused.getMessage());
        verify(cloudinaryService).deleteFile(IMAGE_URL); // compensation for the rollback
        verify(reportMapper, never()).toResponse(any(GarbageReport.class));
    }

    /* The citizen's sighting, in the city value registration stored */
    private CreateReportRequest mohaliRequest() {
        CreateReportRequest request = new CreateReportRequest();
        request.setTitle("Garbage pile near market");
        request.setDescription("Mixed waste dumped on the footpath");
        request.setLatitude(30.7046);
        request.setLongitude(76.7179);
        request.setAddress("Sector 70, Sahibzada Ajit Singh Nagar");
        request.setCity("S.a.s.nagar - Mohali");
        request.setState("Punjab");
        request.setPincode("160071");
        return request;
    }
}
