package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardEntryResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MyLeaderboardResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.BadgeType;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    // User repository
    private final UserRepository userRepository;

    // Cleanup assignment repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;

    @Override
    public LeaderboardResponse getPublicLeaderboard() {

        List<User> cleaners =
                userRepository.findTop10ByRoleOrderByRewardPointsDesc(
                        Role.ROLE_CLEANER
                );

        return buildLeaderboardResponse(
                cleaners,
                LeaderboardType.NATIONAL,
                "India",
                "Top 10 Cleaners in India"
        );
    }

    @Override
    public LeaderboardResponse getStateLeaderboard(String state) {

        state = LocationUtil.normalizeLocation(state);

        List<User> cleaners =
                userRepository.findTop10ByRoleAndStateOrderByRewardPointsDesc(
                        Role.ROLE_CLEANER,
                        state
                );

        if (cleaners.isEmpty()) {

            return LeaderboardResponse.builder()
                    .leaderboardType(LeaderboardType.STATE)
                    .location(state)
                    .message("No leaderboard data available for state '" + state + "'.")
                    .leaderboard(new ArrayList<>())
                    .build();
        }

        return buildLeaderboardResponse(
                cleaners,
                LeaderboardType.STATE,
                state,
                "Top Cleaners in " + state
        );
    }

    @Override
    public LeaderboardResponse getCityLeaderboard(String city) {

        city = LocationUtil.normalizeLocation(city);

        List<User> cleaners =
                userRepository.findTop10ByRoleAndCityOrderByRewardPointsDesc(
                        Role.ROLE_CLEANER,
                        city
                );

        if (cleaners.isEmpty()) {

            return LeaderboardResponse.builder()
                    .leaderboardType(LeaderboardType.CITY)
                    .location(city)
                    .message("No leaderboard data available for city '" + city + "'.")
                    .leaderboard(new ArrayList<>())
                    .build();
        }

        return buildLeaderboardResponse(
                cleaners,
                LeaderboardType.CITY,
                city,
                "Top Cleaners in " + city
        );
    }

    @Override
    public MyLeaderboardResponse getMyRanking() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        // Count cleaners having more reward points
        long higherRankedCleaners =
                userRepository.countByRoleAndRewardPointsGreaterThan(
                        Role.ROLE_CLEANER,
                        cleaner.getRewardPoints()
                );

        // Current cleaner's rank
        int rank = (int) higherRankedCleaners + 1;

        // Total completed cleanups
        long completedCount =
                cleanupAssignmentRepository.countByCleanerAndStatus(
                        cleaner,
                        AssignmentStatus.COMPLETED
                );

        return MyLeaderboardResponse.builder()
                .rank(rank)                                               // Current rank
                .cleanerName(cleaner.getName())                           // Cleaner name
                .rewardPoints(cleaner.getRewardPoints())                  // Cached reward points
                .completedCleanups(completedCount)                        // Completed cleanups

                // Same as completed cleanups for current MVP
                .aiVerifiedCleanups(completedCount)

                // Current badge
                .badge(calculateBadge(cleaner.getRewardPoints()))

                // Remaining points for next badge
                .pointsToNextBadge(
                        calculatePointsToNextBadge(cleaner.getRewardPoints())
                )
                .build();
    }

    /**
     * Determines badge based on
     * total reward points.
     */
    private BadgeType calculateBadge(Integer rewardPoints) {

        // Gold badge
        if (rewardPoints >= 500) {
            return BadgeType.GOLD;
        }

        // Silver badge
        if (rewardPoints >= 200) {
            return BadgeType.SILVER;
        }

        // Bronze badge
        return BadgeType.BRONZE;
    }

    /**
     * Calculates remaining points
     * required for next badge.
     */
    private Integer calculatePointsToNextBadge(Integer rewardPoints) {

        // Bronze → Silver
        if (rewardPoints < 200) {
            return 200 - rewardPoints;
        }

        // Silver → Gold
        if (rewardPoints < 500) {
            return 500 - rewardPoints;
        }

        // Already reached highest badge
        return 0;
    }

    /**
     * Converts cleaner list into
     * leaderboard response.
     */
    private LeaderboardResponse buildLeaderboardResponse(
            List<User> cleaners,
            LeaderboardType leaderboardType,
            String location,
            String message
    ) {

        List<LeaderboardEntryResponse> leaderboard = new ArrayList<>();

        int rank = 1;

        for (User cleaner : cleaners) {

            long completedCount =
                    cleanupAssignmentRepository.countByCleanerAndStatus(
                            cleaner,
                            AssignmentStatus.COMPLETED
                    );

            leaderboard.add(
                    LeaderboardEntryResponse.builder()
                            .rank(rank++)
                            .cleanerName(cleaner.getName())
                            .rewardPoints(cleaner.getRewardPoints())
                            .completedCleanups(completedCount)

                            // Same for current MVP
                            .aiVerifiedCleanups(completedCount)

                            .badge(calculateBadge(cleaner.getRewardPoints()))
                            .build()
            );
        }

        return LeaderboardResponse.builder()
                .leaderboardType(leaderboardType)
                .location(location)
                .message(message)
                .leaderboard(leaderboard)
                .build();
    }


    /**
     * Returns currently
     * authenticated cleaner.
     */
    private User getLoggedInCleaner() {

        // Current authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Fetch cleaner by email
        User cleaner = userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found."));

        // Only cleaners can access leaderboard
        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can access leaderboard.");
        }

        return cleaner;
    }
}