package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardEntryResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.admin.DashboardResponse;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import com.cleanbharat.wastemanagement.dto.admin.UserSummaryResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.exception.RolePromotionNotAllowedException;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.service.deletion.ReportDeletionService;
import java.time.LocalDateTime;
import com.cleanbharat.wastemanagement.exception.UserDeletionNotAllowedException;
import com.cleanbharat.wastemanagement.service.deletion.UserDeletionService;
import com.cleanbharat.wastemanagement.util.PaginationUtil;

import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of all Admin Portal operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

    /*
      Fields either admin register may be ordered by.

      Both lists arrive as request parameters that end up in an ORDER BY
      clause, so they may only ever be one of these literal strings. The
      account register defaults to newest first; the report register has
      the same default, with engagement offered as the alternative the
      trending view ranks by.
    */
    private static final Set<String> USER_SORT_PROPERTIES =
            Set.of("createdAt", "name", "rewardPoints");

    private static final Set<String> REPORT_SORT_PROPERTIES =
            Set.of("createdAt", "engagementScore");

    // User repository
    private final UserRepository userRepository;

    // Garbage report repository
    private final GarbageReportRepository garbageReportRepository;

    // Cleanup assignment repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;

    // Comment repository
    private final CommentRepository commentRepository;

    // Vote repository
    private final VoteRepository voteRepository;

    // Reuse leaderboard module
    private final LeaderboardService leaderboardService;

    // User deletion workflow
    private final UserDeletionService userDeletionService;

    // Shared mapper for Report -> DTO conversion
    private final ReportMapper reportMapper;

    // Handles complete report deletion workflow
    private final ReportDeletionService reportDeletionService;


    /*
     * @Cacheable — the admin overview is ten COUNT queries and a leaderboard read.
     *
     * WHY A SINGLE SHARED KEY IS SAFE HERE:
     *   Nothing in this response depends on WHICH administrator is asking.
     *   Every admin sees the same platform-wide totals, so one entry
     *   ("admin_dashboard_stats::overview") serves all of them.
     *   Contrast getDashboardStats() on the municipal desk, where a shared
     *   key would leak one city's numbers to another.
     *
     * WHAT IT SAVES:
     *   MISS: 10 aggregate COUNT queries against Neon + the leaderboard read.
     *   HIT:  one Redis lookup, no database traffic at all.
     *
     * NESTING IS DELIBERATE:
     *   getPublicLeaderboard() below is itself @Cacheable. On a HIT here it
     *   is never called; on a MISS it usually answers from its own cache.
     *   Both caches are evicted by the same event (rewardCleaner), so the
     *   topCleaner stored here can never drift from the leaderboard page.
     *
     * FRESHNESS:
     *   Every path that moves one of these numbers evicts this cache -
     *   registration, user deletion, role promotion, report create/delete,
     *   cleanup start, AI verification, municipal sign-off, comments and
     *   urgency ratings. The 10 minute TTL is only a safety net.
     */
    @Cacheable(value = "admin_dashboard_stats", key = "'overview'")
    @Override
    public DashboardResponse getDashboard() {

        // Reuse existing leaderboard logic
        LeaderboardResponse leaderboard = leaderboardService.getPublicLeaderboard();

        String topCleaner = null;

        // Get highest ranked cleaner if available
        if (!leaderboard.getLeaderboard().isEmpty()) {

            LeaderboardEntryResponse topEntry = leaderboard.getLeaderboard().getFirst();

            topCleaner = topEntry.getCleanerName();
        }

        return DashboardResponse.builder()

                // User statistics
                .totalUsers(userRepository.count())
                .totalCitizens(userRepository.countByRole(Role.ROLE_CITIZEN))
                .totalCleaners(userRepository.countByRole(Role.ROLE_CLEANER))
                .totalAdmins(userRepository.countByRole(Role.ROLE_ADMIN))

                // Report statistics
                .totalReports(garbageReportRepository.count())
                .pendingReports(
                        garbageReportRepository.countByStatus(ReportStatus.PENDING)
                )
                .completedReports(
                        garbageReportRepository.countByStatus(ReportStatus.RESOLVED)
                )

                // Cleanup statistics
                .verifiedCleanups(cleanupAssignmentRepository.countByAiVerifiedTrue())

                // Community statistics
                .totalComments(commentRepository.count())
                // Ratings only: a row recording a like of a cleanup
                // is not a vote
                .totalVotes(voteRepository.countByRatingIsNotNull())

                // Leaderboard
                .topCleaner(topCleaner)

                .build();
    }

    @Override
    public PageResponse<UserSummaryResponse> getUsers(
            Role role,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        // Newest account first, unless a whitelisted field is asked for
        Pageable pageable = PaginationUtil.resolve(
                page,
                size,
                PaginationUtil.resolveSort(
                        sortBy,
                        direction,
                        USER_SORT_PROPERTIES,
                        "createdAt"
                )
        );

        Page<User> users = role == null
                ? userRepository.findAllBy(pageable)
                : userRepository.findByRole(role, pageable);

        return PageResponse.from(users, this::mapToUserSummaryResponse);
    }

    @Override
    public PageResponse<UserSummaryResponse> searchUsers(
            String keyword,
            Role role,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Pageable pageable = PaginationUtil.resolve(
                page,
                size,
                PaginationUtil.resolveSort(
                        sortBy,
                        direction,
                        USER_SORT_PROPERTIES,
                        "createdAt"
                )
        );

        // Search across every role, or within the selected one
        Page<User> users = role == null
                ? userRepository.findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        keyword,
                        keyword,
                        pageable
                )
                : userRepository.findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
                        role,
                        keyword,
                        role,
                        keyword,
                        pageable
                );

        return PageResponse.from(users, this::mapToUserSummaryResponse);
    }

    @Override
    public UserDetailsResponse getUserDetails(Long userId) {

        // Find user by ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID : " + userId));

        // Total completed cleanups
        long completedCleanups =
                cleanupAssignmentRepository.countByCleanerAndStatus(
                        user,
                        AssignmentStatus.COMPLETED
                );

        // Total reports created
        long reportsCreated = garbageReportRepository.countByUser(user);

        // Total comments written
        long comments = commentRepository.countByUser(user);

        // Total votes submitted
        // Excludes rows that only record a like of a cleanup
        long votes = voteRepository.countByUserAndRatingIsNotNull(user);

        // Convert to response DTO
        return mapToUserDetailsResponse(
                user,
                completedCleanups,
                reportsCreated,
                comments,
                votes
        );
    }

    @Override
    @Transactional
    public SuccessResponse deleteUser(Long userId) {

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID : " + userId));

        /*
         * Admin accounts cannot be deleted through the application.
         */
        if (user.getRole() == Role.ROLE_ADMIN) {
            throw new UserDeletionNotAllowedException("Admin accounts cannot be deleted through the application.");
        }

        /*
         * Citizens
         */
        if (user.getRole() == Role.ROLE_CITIZEN) {

            userDeletionService.deleteCitizen(user);

            return SuccessResponse.builder()
                    .message("Citizen deleted successfully.")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
        }

        /*
         * Cleaners
         */
        if (user.getRole() == Role.ROLE_CLEANER) {
            /*
             * Prevent deletion when the cleaner has participated in any cleanup.
             */
            boolean hasAssignments = !cleanupAssignmentRepository
                            .findByCleaner(user)
                            .isEmpty();

            if (hasAssignments) {
                throw new UserDeletionNotAllowedException("Cleaner cannot be deleted because cleanup assignments are associated with this account.");
            }

            userDeletionService.deleteCleaner(user);

            return SuccessResponse.builder()
                    .message("Cleaner deleted successfully.")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();
        }

        /*
         * Future-proof fallback.
         */
        throw new UserDeletionNotAllowedException("User cannot be deleted.");
    }

    @Override
    public PageResponse<ReportResponse> searchReports(
            String keyword,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Pageable pageable = PaginationUtil.resolve(
                page,
                size,
                PaginationUtil.resolveSort(
                        sortBy,
                        direction,
                        REPORT_SORT_PROPERTIES,
                        "createdAt"
                )
        );

        // Search reports using keyword
        Page<GarbageReport> reports =
                garbageReportRepository.searchReportsPaged(keyword, pageable);

        // Entity -> DTO
        return PageResponse.from(reports, reportMapper::toResponse);
    }

    @Override
    public PageResponse<ReportResponse> filterReports(
            ReportStatus status,
            String city,
            String state,
            int page,
            int size,
            String sortBy,
            String direction
    ) {

        Pageable pageable = PaginationUtil.resolve(
                page,
                size,
                PaginationUtil.resolveSort(
                        sortBy,
                        direction,
                        REPORT_SORT_PROPERTIES,
                        "createdAt"
                )
        );

        // Filter reports using optional parameters
        Page<GarbageReport> reports =
                garbageReportRepository.filterReportsPaged(
                        status,
                        city,
                        state,
                        pageable
                );

        // Entity -> DTO
        return PageResponse.from(reports, reportMapper::toResponse);
    }

    @Override
    @Transactional
    public SuccessResponse deleteReport(Long reportId) {

        // Find report
        GarbageReport report = garbageReportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with ID : " + reportId));

        // Delegate complete deletion workflow
        reportDeletionService.deleteReport(report);

        return SuccessResponse.builder()
                .message("Report deleted successfully.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    // Role changes in place, so no deletion service is involved: this method
    // is the only place the citizen/admin split moves, and must evict itself
    @CacheEvict(value = "admin_dashboard_stats", allEntries = true)
    @Override
    @Transactional
    public SuccessResponse promoteCitizenToAdmin(Long userId) {

        // Find user
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID : " + userId));

        /*
         * Only citizens can become admins.
         */
        if (user.getRole() != Role.ROLE_CITIZEN) {
            throw new RolePromotionNotAllowedException("Only citizens can be promoted to Admin.");
        }

        // Promote citizen
        promoteCitizen(user);

        return SuccessResponse.builder()
                .message("Citizen promoted to Admin successfully.")
                .timestamp(LocalDateTime.now())
                .build();
    }


    /**
     * Promotes a citizen to Admin.
     */
    private void promoteCitizen(User citizen) {

        // Update role
        citizen.setRole(Role.ROLE_ADMIN);

        // Persist change
        userRepository.save(citizen);
    }


    /**
     * Converts User entity into
     * UserSummaryResponse DTO.
     */
    private UserSummaryResponse mapToUserSummaryResponse(User user) {

        return UserSummaryResponse.builder()

                // Basic information
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())

                // User role
                .role(user.getRole())

                // User location
                .state(user.getState())
                .city(user.getCity())

                // Reward information
                .rewardPoints(user.getRewardPoints())

                // Account creation date
                .createdAt(user.getCreatedAt())

                .build();
    }


    /**
     * Converts User entity into UserDetailsResponse.
     */
    private UserDetailsResponse mapToUserDetailsResponse(
            User user,
            long completedCleanups,
            long reportsCreated,
            long comments,
            long votes
    ) {

        return UserDetailsResponse.builder()

                // Basic information
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())

                // Role information
                .role(user.getRole())
                .cleanerType(user.getCleanerType())

                // Organization
                .organizationName(user.getOrganizationName())

                // Location
                .state(user.getState())
                .city(user.getCity())

                // Reward points
                .rewardPoints(user.getRewardPoints())

                // Activity statistics
                .completedCleanups(completedCleanups)
                .reportsCreated(reportsCreated)
                .comments(comments)
                .votes(votes)

                // Registration date
                .createdAt(user.getCreatedAt())

                .build();
    }
}