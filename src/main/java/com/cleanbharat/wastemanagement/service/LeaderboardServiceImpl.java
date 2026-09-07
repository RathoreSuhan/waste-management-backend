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
import org.springframework.cache.annotation.Cacheable;
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

    /*
     * @Cacheable(value = "leaderboard_top", key = "'national'")
     *
     * WHAT THIS DOES:
     *   Caches the national leaderboard under key 'national' inside
     *   the 'leaderboard_top' cache namespace.
     *
     * CACHE HIT:
     *   Redis returns the LeaderboardResponse directly (~2ms).
     *   No database query. No sorting. No rank computation.
     *
     * CACHE MISS:
     *   Executes findTop10ByRoleOrderByRewardPointsDesc → DB query,
     *   then iterates to compute completedCount per cleaner,
     *   builds the response, stores in Redis, returns result.
     *
     * WHY CACHE THIS:
     *   - National leaderboard is shown on the PUBLIC homepage.
     *   - Accessed by every visitor, every few seconds.
     *   - Query requires sorting ALL cleaners by rewardPoints DESC,
     *     then N+1 queries for completedCount per cleaner.
     *   - Data changes only when rewardCleaner() runs (rare event).
     *   - Ideal candidate: high-read, low-write → CACHE HIT rate ~99%.
     *
     * KEY STRATEGY:
     *   SpEL key = "'national'" → constant string key for national.
     *   For city variant: "'city:' + #city.toLowerCase()".
     *   This creates separate cache entries per geographic level,
     *   so invalidating city leaderboard doesn't affect national.
     *
     * TTL:
     *   5 minutes (configured in RedisConfig per-cache override).
     *   If @CacheEvict is missed (edge case), stale ranks expire
     *   within 5 minutes automatically.
     * ============================================================
     */
    @Cacheable(value = "leaderboard_top", key = "'national'")
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

    /*
     * @Cacheable(value = "leaderboard_top", key = "'state:' + #state.toLowerCase()")
     *
     * KEY GENERATION (SpEL):
     *   #state → method parameter 'state'
     *   .toLowerCase() → normalize so "Maharashtra" and "maharashtra"
     *     map to the SAME cache key ("state:maharashtra").
     *   Without normalization: "Maharashtra" and "maharashtra" would
     *     be TWO separate cache entries → wasted memory + stale data.
     *
     * CACHE HIT: Returns cached state leaderboard (~2ms).
     * CACHE MISS: Executes DB query with ORDER BY rewardPoints DESC.
     *
     * WHY THIS MATTERS FOR 30MB LIMIT:
     *   Each state leaderboard is a List<LeaderboardEntryResponse>.
     *   ~10 entries × ~500 bytes each = ~5KB per state.
     *   With 30+ states: if every state is queried once, that's
     *   ~150KB of cached data — still well within 30MB.
     *   But with TTL=5min, only the most-recently-accessed states
     *   stay cached. Least-recently-used entries auto-expire.
     * ============================================================
     */
    @Cacheable(value = "leaderboard_top", key = "'state:' + #state.toLowerCase()")
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

    /*
     * @Cacheable(value = "leaderboard_top", key = "'city:' + #city.toLowerCase()")
     *
     * CITY LEADERBOARD CACHING:
     *   Same pattern as state leaderboard.
     *   Key = "city:kolkata", "city:delhi", etc.
     *
     * WHY SEPARATE KEYS FOR EACH LEVEL:
     *   National, state, and city leaderboards are stored
     *   in the SAME Redis cache (leaderboard_top) but with
     *   different key prefixes. This keeps related data
     *   grouped while preventing cross-contamination.
     *
     *   When a cleaner earns points (rewardCleaner runs):
     *     @CacheEvict(value = "leaderboard_top", allEntries = true)
     *   evicts ALL leaderboard entries (national, all states, all cities).
     *   This guarantees NO stale rankings anywhere.
     * ============================================================
     */
    @Cacheable(value = "leaderboard_top", key = "'city:' + #city.toLowerCase()")
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