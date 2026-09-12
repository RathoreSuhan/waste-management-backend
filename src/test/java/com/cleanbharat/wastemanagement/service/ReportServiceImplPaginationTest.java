package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MyReportSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.ai.AIReportValidationService;
import com.cleanbharat.wastemanagement.service.location.ReportDuplicateValidationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the paged report paths.
 *
 * Focus: the register routes through the finder matching the asked filters,
 * never asks for more than the clamp allows, and the summary tiles come from
 * one grouped query rather than a count per status.
 */
@ExtendWith(MockitoExtension.class)
class ReportServiceImplPaginationTest {

    @Mock private GarbageReportRepository reportRepository;
    @Mock private UserRepository userRepository;
    @Mock private CloudinaryService cloudinaryService;
    @Mock private CleanupAssignmentService cleanupAssignmentService;
    @Mock private AIReportValidationService aiReportValidationService;
    @Mock private ReportDuplicateValidationService reportDuplicateValidationService;

    private ReportServiceImpl reportService;

    @BeforeEach
    void buildServiceWithRealMapper() {
        // The mapper is real - the point is to check the mapping runs, not
        // that a mocked mapper passes its result through untouched.
        reportService = new ReportServiceImpl(
                reportRepository,
                userRepository,
                cloudinaryService,
                cleanupAssignmentService,
                new ReportMapper(),
                aiReportValidationService,
                reportDuplicateValidationService
        );
    }

    @AfterEach
    void clearSecurityContext() {
        // Tests share one thread, so never leak a principal
        SecurityContextHolder.clearContext();
    }

    @Test
    void unfilteredRegisterUsesThePlainPageableFinder() {

        User reporter = user("Priya Sharma");
        GarbageReport report = report(reporter);

        when(reportRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        PageResponse<ReportResponse> response =
                reportService.getAllReports(0, 10, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAllBy(pageableCaptor.capture());

        // Newest first, with the id breaking ties between same-second reports
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending());
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending());

        // The mapper ran: the reporter's name is on the DTO, the id carried over
        assertEquals(1, response.content().size());
        assertEquals("Priya Sharma", response.content().get(0).getReportedBy());
        assertEquals(report.getId(), response.content().get(0).getId());
        assertEquals(1, response.totalElements());
    }

    @Test
    void keywordRoutesThroughTheSearchFinder() {

        when(reportRepository.searchReportsPaged(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report(user("Amit Verma")))));

        PageResponse<ReportResponse> response =
                reportService.getAllReports(0, 10, null, null, "patna", null);

        verify(reportRepository).searchReportsPaged(any(), any(Pageable.class));
        assertEquals(1, response.totalElements());
    }

    @Test
    void statusRoutesThroughThePagedFilterFinder() {

        when(reportRepository.filterReportsPaged(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report(user("Rohan Das")))));

        PageResponse<ReportResponse> response =
                reportService.getAllReports(0, 10, null, null, null, ReportStatus.PENDING);

        verify(reportRepository).filterReportsPaged(any(), any(), any(), any(Pageable.class));
        assertEquals(1, response.totalElements());
    }

    @Test
    void oversizedPageSizeIsClampedBeforeItReachesTheDatabase() {

        when(reportRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report(user("Meera Nair")))));

        reportService.getAllReports(0, 999, null, null, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAllBy(pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void engagementSortIsWhitelisted() {

        when(reportRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        reportService.getAllReports(0, 10, "engagementScore", "asc", null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(reportRepository).findAllBy(pageableCaptor.capture());
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("engagementScore").isAscending());
    }
// ------------------------------------------------------------------
    // /api/reports/my - the citizen's own reports
    // ------------------------------------------------------------------

    @Test
    void myReportsApplyTheStatusFilterWhenRequested() {

        User citizen = user("Kavita Iyer");
        authenticateAs(citizen.getEmail());

        when(userRepository.findByEmail(citizen.getEmail())).thenReturn(java.util.Optional.of(citizen));
        when(reportRepository.findByUserAndStatus(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report(citizen))));

        PageResponse<ReportResponse> response =
                reportService.getMyReports(0, 10, ReportStatus.RESOLVED);

        verify(reportRepository).findByUserAndStatus(any(), any(), any(Pageable.class));
        assertEquals(1, response.totalElements());
    }

    @Test
    void myReportsSummaryCountsAreGroupedInOneQuery() {

        User citizen = user("Kavita Iyer");
        authenticateAs(citizen.getEmail());

        // One row per status the citizen has actually used - the rest read as zero
        GarbageReportRepository.StatusCount pending = statusCount(ReportStatus.PENDING, 3);
        GarbageReportRepository.StatusCount inProgress = statusCount(ReportStatus.IN_PROGRESS, 2);

        when(userRepository.findByEmail(citizen.getEmail())).thenReturn(java.util.Optional.of(citizen));
        when(reportRepository.countByUserGroupedByStatus(citizen))
                .thenReturn(List.of(pending, inProgress));

        MyReportSummaryResponse summary = reportService.getMyReportsSummary();

        // Three counts in one grouped query, not three queries
        verify(reportRepository).countByUserGroupedByStatus(citizen);

        assertEquals(5, summary.getTotal());
        assertEquals(3, summary.getPending());
        assertEquals(2, summary.getInProgress());
        assertEquals(0, summary.getResolved());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private GarbageReportRepository.StatusCount statusCount(ReportStatus status, long total) {
        return new GarbageReportRepository.StatusCount() {
            @Override
            public ReportStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private User user(String name) {
        return User.builder()
                .id(11L)
                .name(name)
                .email(name.toLowerCase().replace(" ", ".") + "@example.com")
                .role(Role.ROLE_CITIZEN)
                .rewardPoints(0)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private GarbageReport report(User reporter) {
        return GarbageReport.builder()
                .id(7L)
                .title("Garbage pile near the market")
                .description("Plastic and organic waste dumped on the roadside.")
                .latitude(25.6)
                .longitude(85.1)
                .address("Main Market Road")
                .city("Patna")
                .state("Bihar")
                .pincode("800001")
                .status(ReportStatus.PENDING)
                .urgencyScore(4.0)
                .engagementScore(12.0)
                .user(reporter)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Puts a JWT-style principal in the context, which is all the service reads.
     */
    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
        SecurityContextHolder.setContext(context);
    }
}