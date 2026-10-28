package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidReportCreationException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AIReportValidationService;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the report-creation path that decides where a report goes.
 *
 * Two submission shapes exist (location refactor):
 *
 * Legacy (free-text city/state): the report row is saved before the municipal
 * destination is resolved from the stored city, both inside one service-level
 * transaction; a missing destination is a clear error naming the city, no
 * response is ever mapped, and the uploaded image is removed from Cloudinary.
 *
 * Code-backed (directory cascade): the triple is validated and the
 * destination is resolved by ULB code BEFORE any AI call, duplicate check or
 * upload, so an onboarded-less ULB is refused cheaply with no partial report,
 * and the resolved office is handed to the assignment step.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final String CITIZEN_EMAIL = "citizen.routing@example.com";
    private static final String IMAGE_URL = "https://cloudinary.test/report.jpg";

    @Mock private GarbageReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private MunicipalCorporationRepository municipalCorporationRepository;
    @Mock private IndianLocationService indianLocationService;
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
                .district("S.a.s Nagar")
                .ulbCode("248325")
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

    private AIReportValidationResponse passingAiValidation() {
        return AIReportValidationResponse.builder()
                .garbageDetected(true)
                .validReportImage(true)
                .garbageCategory("Mixed Garbage")
                .build();
    }

    // ---------------------------------------------------------------------
    // LEGACY SHAPE: free-text city/state, destination resolved at assignment
    // ---------------------------------------------------------------------

    @Test
    void theReportIsSavedBeforeItsMunicipalDestinationIsResolved() {
        // Canonical Mohali city value, exactly as registration under ULB
        // 248325 stores it
        CreateReportRequest request = mohaliLegacyRequest();
        GarbageReport saved = GarbageReport.builder().id(21L).city(request.getCity()).build();

        when(aiReportValidationService.validateReportImage(image)).thenReturn(passingAiValidation());
        when(cloudinaryService.uploadFile(image)).thenReturn(IMAGE_URL);
        when(reportRepository.save(any(GarbageReport.class))).thenReturn(saved);

        reportService.createReport(request, image);

        // Resolution reads the saved row, so this order matters - and the
        // transaction spans both steps, which is what makes a later failure
        // roll the saved row back. Legacy reports resolve their destination
        // inside the assignment step, hence the null pre-resolved office.
        InOrder pipeline = inOrder(reportRepository, cleanupAssignmentService);
        pipeline.verify(reportRepository).save(any(GarbageReport.class));
        pipeline.verify(cleanupAssignmentService).createDefaultAssignment(eq(saved), isNull());
    }

    @Test
    void aReportWithoutAMunicipalDestinationIsRefusedAndTheUploadIsRemoved() {
        // A ULB selectable in the directory can still have no Clean Bharat
        // MunicipalCorporation row. The failure must name the city, reach no
        // response mapping, and the already-uploaded image must be cleaned up.
        CreateReportRequest request = mohaliLegacyRequest();
        when(aiReportValidationService.validateReportImage(image)).thenReturn(passingAiValidation());
        when(cloudinaryService.uploadFile(image)).thenReturn(IMAGE_URL);
        when(reportRepository.save(any(GarbageReport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new ResourceNotFoundException(
                "No Municipal Corporation found for city: S.a.s.nagar - Mohali"))
                .when(cleanupAssignmentService)
                .createDefaultAssignment(any(GarbageReport.class), isNull());

        ResourceNotFoundException refused = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.createReport(request, image));

        assertTrue(refused.getMessage().contains("S.a.s.nagar - Mohali"), refused.getMessage());
        verify(cloudinaryService).deleteFile(IMAGE_URL); // compensation for the rollback
        verify(reportMapper, never()).toResponse(any(GarbageReport.class));
    }

    @Test
    void aLegacyReportMissingCityOrStateIsRefused() {
        CreateReportRequest request = mohaliLegacyRequest();
        request.setCity(null); // neither codes nor a free-text city

        InvalidReportCreationException refused = assertThrows(
                InvalidReportCreationException.class,
                () -> reportService.createReport(request, image));

        assertTrue(refused.getMessage().contains("City and state are required"), refused.getMessage());
        verify(aiReportValidationService, never()).validateReportImage(any());
        verify(cloudinaryService, never()).uploadFile(any());
    }

    // ---------------------------------------------------------------------
    // CODE-BACKED SHAPE: directory cascade, destination resolved up front
    // ---------------------------------------------------------------------

    @Test
    void aCodeBackedReportResolvesItsDestinationBeforeAnyExpensiveWork() {
        CreateReportRequest request = mohaliCodedRequest();
        stubDirectoryTriple();
        MunicipalCorporation mohali = mohaliCorporation();
        when(municipalCorporationRepository.findByUlbCode("248325")).thenReturn(Optional.of(mohali));
        when(aiReportValidationService.validateReportImage(image)).thenReturn(passingAiValidation());
        GarbageReport saved = GarbageReport.builder().id(22L).build();

        when(cloudinaryService.uploadFile(image)).thenReturn(IMAGE_URL);
        when(reportRepository.save(any(GarbageReport.class))).thenReturn(saved);

        reportService.createReport(request, image);

        // The destination lookup happens before the AI call: an onboarded-less
        // ULB must never cost an AI request, a duplicate query or an upload
        InOrder order = inOrder(municipalCorporationRepository, aiReportValidationService,
                reportDuplicateValidationService, cloudinaryService, cleanupAssignmentService);
        order.verify(municipalCorporationRepository).findByUlbCode("248325");
        order.verify(aiReportValidationService).validateReportImage(image);
        order.verify(reportDuplicateValidationService).validateNoDuplicateReport(request);
        order.verify(cloudinaryService).uploadFile(image);
        order.verify(cleanupAssignmentService).createDefaultAssignment(saved, mohali);

        // The stored names come from the directory, never from the client's text
        var savedReport = org.mockito.ArgumentCaptor.forClass(GarbageReport.class);
        verify(reportRepository).save(savedReport.capture());
        assertEquals("S.a.s.nagar - Mohali", savedReport.getValue().getCity());
        assertEquals("Punjab", savedReport.getValue().getState());
        assertEquals("S.a.s Nagar", savedReport.getValue().getDistrict());
        assertEquals("248325", savedReport.getValue().getUlbCode());
    }

    @Test
    void anUnonboardedUlbIsRefusedBeforeAiDuplicateOrUpload() {
        CreateReportRequest request = mohaliCodedRequest();
        stubDirectoryTriple();
        when(municipalCorporationRepository.findByUlbCode("248325")).thenReturn(Optional.empty());

        ResourceNotFoundException refused = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.createReport(request, image));

        // Actionable: names the municipal area and makes clear nothing was saved
        assertTrue(refused.getMessage().contains("not onboarded yet"), refused.getMessage());
        assertTrue(refused.getMessage().contains("S.a.s.nagar - Mohali"), refused.getMessage());
        verify(aiReportValidationService, never()).validateReportImage(any());
        verify(reportDuplicateValidationService, never()).validateNoDuplicateReport(any());
        verify(cloudinaryService, never()).uploadFile(any());
        verify(reportRepository, never()).save(any(GarbageReport.class));
    }

    @Test
    void anInvalidTripleIsRefusedBeforeAnyWork() {
        CreateReportRequest request = mohaliCodedRequest();
        when(indianLocationService.resolve("3", "608", "248325"))
                .thenThrow(new com.cleanbharat.wastemanagement.exception.InvalidRegistrationException(
                        "The selected district is not in Punjab."));

        assertThrows(com.cleanbharat.wastemanagement.exception.InvalidRegistrationException.class,
                () -> reportService.createReport(request, image));

        verify(municipalCorporationRepository, never()).findByUlbCode(any());
        verify(aiReportValidationService, never()).validateReportImage(any());
        verify(cloudinaryService, never()).uploadFile(any());
        verify(reportRepository, never()).save(any(GarbageReport.class));
    }

    @Test
    void partiallySentCodesAreRefused() {
        CreateReportRequest request = mohaliCodedRequest();
        request.setDistrictCode(null); // three codes must travel together

        InvalidReportCreationException refused = assertThrows(
                InvalidReportCreationException.class,
                () -> reportService.createReport(request, image));

        assertTrue(refused.getMessage().contains("must be sent together"), refused.getMessage());
        verify(aiReportValidationService, never()).validateReportImage(any());
    }

    // ---------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------

    /* The citizen's sighting, in the city value registration stored */
    private CreateReportRequest mohaliLegacyRequest() {
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

    /* Same sighting, submitted through the directory cascade */
    private CreateReportRequest mohaliCodedRequest() {
        CreateReportRequest request = mohaliLegacyRequest();
        request.setCity(null); // the cascade form sends codes, not free text
        request.setState(null);
        request.setStateCode("3");
        request.setDistrictCode("608");
        request.setUlbCode("248325");
        return request;
    }

    private void stubDirectoryTriple() {
        when(indianLocationService.resolve("3", "608", "248325"))
                .thenReturn(new com.cleanbharat.wastemanagement.service.location.IndianLocationService.ResolvedLocation(
                        "Punjab", "S.a.s Nagar", "S.a.s.nagar - Mohali", "248325"));
    }

    private MunicipalCorporation mohaliCorporation() {
        return MunicipalCorporation.builder()
                .id(9L)
                .city("Mohali") // a legacy display name survives the mapping
                .ulbCode("248325")
                .organizationName("Municipal Corporation SAS Nagar Mohali")
                .build();
    }
}
