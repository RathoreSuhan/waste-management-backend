package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserSummaryResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import com.cleanbharat.wastemanagement.service.deletion.ReportDeletionService;
import com.cleanbharat.wastemanagement.service.deletion.UserDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the paged admin registers.
 *
 * Focus: both registers clamp the page size exactly like the public ones,
 * route through the finder matching the filter asked for, and carry the
 * totals through to the response envelope.
 */
@ExtendWith(MockitoExtension.class)
class AdminServicePaginationTest {

    @Mock private UserRepository userRepository;
    @Mock private GarbageReportRepository garbageReportRepository;
    @Mock private CleanupAssignmentRepository cleanupAssignmentRepository;
    @Mock private CommentRepository commentRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private LeaderboardService leaderboardService;
    @Mock private UserDeletionService userDeletionService;
    @Mock private ReportDeletionService reportDeletionService;

    private AdminServiceImpl adminService;

    @BeforeEach
    void buildServiceWithRealMappers() {
        // mappers are real - the point is to check the mapping runs
        adminService = new AdminServiceImpl(
                userRepository,
                garbageReportRepository,
                cleanupAssignmentRepository,
                commentRepository,
                voteRepository,
                leaderboardService,
                userDeletionService,
                new ReportMapper(),
                reportDeletionService
        );
    }

    @Test
    void allUsersArePagedNewestFirst() {

        when(userRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        User.builder()
                                .id(3L)
                                .name("Suresh Kumar")
                                .email("suresh@example.com")
                                .role(Role.ROLE_CLEANER)
                                .state("Bihar")
                                .city("Patna")
                                .rewardPoints(120)
                                .createdAt(LocalDateTime.now())
                                .build()
                )));

        PageResponse<UserSummaryResponse> response =
                adminService.getUsers(null, 0, 10, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllBy(pageableCaptor.capture());

        // The account register defaults to newest first, id breaking ties
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending());
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending());

        assertEquals(1, response.content().size());
        assertEquals("suresh@example.com", response.content().get(0).getEmail());
        assertEquals(Role.ROLE_CLEANER, response.content().get(0).getRole());
    }

    @Test
    void roleFilterRoutesThroughTheRoleFinder() {

        when(userRepository.findByRole(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<UserSummaryResponse> response =
                adminService.getUsers(Role.ROLE_CITIZEN, 0, 10, null, null);

        verify(userRepository).findByRole(eq(Role.ROLE_CITIZEN), any(Pageable.class));
        assertEquals(0, response.totalElements());
    }

    @Test
    void userPageSizeIsClamped() {

        when(userRepository.findAllBy(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.getUsers(null, 1, 999, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllBy(pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }
@Test
    void userSearchAcrossEveryRoleUsesTheBroadFinder() {

        when(userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.searchUsers("amit", null, 0, 10, null, null);

        verify(userRepository).findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                eq("amit"), eq("amit"), any(Pageable.class));
    }

    @Test
    void userSearchWithinARoleUsesTheRoleScopedFinder() {

        when(userRepository.findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
                any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        adminService.searchUsers("amit", Role.ROLE_CLEANER, 0, 10, null, null);

        verify(userRepository).findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
                eq(Role.ROLE_CLEANER), eq("amit"), eq(Role.ROLE_CLEANER), eq("amit"), any(Pageable.class));
    }

    @Test
    void reportFilterIsPagedAndMapsToDtos() {

        User reporter = User.builder()
                .name("Neha Gupta")
                .build();

        GarbageReport report = GarbageReport.builder()
                .id(5L)
                .title("Dump near the temple")
                .description("Construction waste on the temple lane.")
                .latitude(26.0)
                .longitude(85.0)
                .address("Temple Lane")
                .city("Gaya")
                .state("Bihar")
                .pincode("823001")
                .status(ReportStatus.IN_PROGRESS)
                .urgencyScore(3.0)
                .engagementScore(4.0)
                .user(reporter)
                .createdAt(LocalDateTime.now())
                .build();

        when(garbageReportRepository.filterReportsPaged(
                any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(report)));

        PageResponse<ReportResponse> response =
                adminService.filterReports(
                        ReportStatus.IN_PROGRESS,
                        "Gaya",
                        null,
                        0,
                        10,
                        null,
                        null
                );

        verify(garbageReportRepository).filterReportsPaged(
                eq(ReportStatus.IN_PROGRESS),
                eq("Gaya"),
                isNull(),
                any(Pageable.class)
        );

        assertEquals("Neha Gupta", response.content().get(0).getReportedBy());
        assertEquals(1, response.totalElements());
    }

    @Test
    void reportSearchIsPaged() {

        when(garbageReportRepository.searchReportsPaged(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ReportResponse> response =
                adminService.searchReports("patna", 0, 10, null, null);

        verify(garbageReportRepository).searchReportsPaged(eq("patna"), any(Pageable.class));
        assertEquals(0, response.totalElements());
    }
}