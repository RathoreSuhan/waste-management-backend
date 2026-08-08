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
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
import com.cleanbharat.wastemanagement.service.deletion.ReportDeletionService;
import java.time.LocalDateTime;
import com.cleanbharat.wastemanagement.exception.UserDeletionNotAllowedException;
import com.cleanbharat.wastemanagement.service.deletion.UserDeletionService;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of all Admin Portal operations.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminServiceImpl implements AdminService {

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
                .totalVotes(voteRepository.count())

                // Leaderboard
                .topCleaner(topCleaner)

                .build();
    }

    @Override
    public List<UserSummaryResponse> getAllUsers() {

        // Fetch every user from database
        List<User> users = userRepository.findAll();

        // Convert entities into DTOs
        return users.stream()
                .map(this::mapToUserSummaryResponse)
                .toList();
    }

    @Override
    public List<UserSummaryResponse> getUsersByRole(Role role) {

        // Fetch users belonging to the given role
        List<User> users = userRepository.findByRole(role);

        // Convert entities into DTOs
        return users.stream()
                .map(this::mapToUserSummaryResponse)
                .toList();
    }

    @Override
    public List<UserSummaryResponse> searchUsers(String keyword, Role role) {
        List<User> users;

        // Search across every role
        if (role == null) {
            users = userRepository
                    .findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                            keyword,
                            keyword
                    );
        }

        // Search only within selected role
        else {
            users = userRepository
                    .findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
                            role,
                            keyword,
                            role,
                            keyword
                    );
        }

        return users.stream()
                .map(this::mapToUserSummaryResponse)
                .toList();
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
        long votes = voteRepository.countByUser(user);

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
    public List<ReportResponse> searchReports(String keyword) {

        // Search reports using keyword
        List<GarbageReport> reports = garbageReportRepository.searchReports(keyword);

        // Entity -> DTO
        return reports.stream()
                .map(reportMapper::toResponse)
                .toList();
    }

    @Override
    public List<ReportResponse> filterReports(
            ReportStatus status,
            String city,
            String state
    ) {

        // Filter reports using optional parameters
        List<GarbageReport> reports =
                garbageReportRepository.filterReports(
                        status,
                        city,
                        state
                );

        // Entity -> DTO
        return reports.stream()
                .map(reportMapper::toResponse)
                .toList();
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