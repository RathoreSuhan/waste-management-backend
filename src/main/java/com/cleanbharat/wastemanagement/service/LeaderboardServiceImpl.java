package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardEntryResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MyLeaderboardResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.BadgeType;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.LocationDataUnavailableException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
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

    // The location directory: resolves a submitted city name to its official ULB identity
    private final IndianLocationService indianLocationService;

    /*
     * The boards rank the top ten cleaners by reward points; the ordering
     * itself lives in each finder's JPQL, so this fixed first page only
     * carries the limit.
     */
    private static final PageRequest TOP_TEN = PageRequest.of(0, 10);

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
                userRepository.findTop10ByRoleOrderByRewardPointsDescIdAsc(
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

        /*
         * Trimmed, never title-cased: the frontend submits the directory's
         * canonical spelling, and folding "S.A.S Nagar" into "S.a.s Nagar"
         * would corrupt it. Matching tolerates casing and stray whitespace
         * in the finder (LOWER(TRIM(...)) on both sides); display keeps the
         * submitted label exactly as the directory spells it.
         */
        state = state.trim();

        List<User> cleaners =
                userRepository.findTop10InState(Role.ROLE_CLEANER, state, TOP_TEN);

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
     *   Same pattern as the state leaderboard. The key derives from the
     *   submitted name, so casing variants collapse into one entry and the
     *   resolution inside the method (a pure function of the input) can
     *   never disagree with the key.
     *
     * WHY SEPARATE KEYS FOR EACH LEVEL:
     *   National, state, city, and district leaderboards are stored
     *   in the SAME Redis cache (leaderboard_top) but with
     *   different key prefixes. This keeps related data
     *   grouped while preventing cross-contamination.
     *
     *   When a cleaner earns points (rewardCleaner runs):
     *     @CacheEvict(value = "leaderboard_top", allEntries = true)
     *   evicts ALL leaderboard entries (national, all states, cities,
     *   districts). This guarantees NO stale rankings anywhere.
     * ============================================================
     */
    @Cacheable(value = "leaderboard_top", key = "'city:' + #city.toLowerCase()")
    @Override
    public LeaderboardResponse getCityLeaderboard(String city) {

        /*
         * Two supported shapes, deliberately not merged:
         *
         * OFFICIAL ULB NAME - the submitted name resolves through the
         * location directory to one ULB code, and the board is read by that
         * identity. Rows keep whatever legacy city value they were
         * registered with (e.g. "Mohali"), so a code-backed read is the only
         * way an official name can find them. The board displays the
         * directory's canonical ULB name, never a re-folded form.
         *
         * LEGACY CITY - anything that does not resolve to exactly one
         * directory ULB (unknown, ambiguous, or the directory being
         * unavailable) searches the stored city value the way the board
         * always has, now tolerant of casing and stray whitespace.
         *
         * The scope stays one municipal jurisdiction either way: a ULB
         * search never widens into its district.
         */
        city = city.trim();

        String ulbCode = null;
        String canonicalUlbName = null;

        try {
            IndianLocationService.NameMatch match =
                    indianLocationService.matchByCityName(city);

            if (match.mapped()) {
                ulbCode = match.location().ulbCode();
                canonicalUlbName = match.location().city();
            }
        } catch (LocationDataUnavailableException unavailable) {
            // The directory is down: fall back to the legacy city search
            // rather than failing the whole public page.
        }

        if (ulbCode != null) {

            List<User> cleaners =
                    userRepository.findTop10ByUlbCode(Role.ROLE_CLEANER, ulbCode, TOP_TEN);

            if (cleaners.isEmpty()) {

                return LeaderboardResponse.builder()
                        .leaderboardType(LeaderboardType.CITY)
                        .location(canonicalUlbName)
                        .message("No leaderboard data available for city '" + canonicalUlbName + "'.")
                        .leaderboard(new ArrayList<>())
                        .build();
            }

            return buildLeaderboardResponse(
                    cleaners,
                    LeaderboardType.CITY,
                    canonicalUlbName,
                    "Top Cleaners in " + canonicalUlbName
            );
        }

        List<User> cleaners =
                userRepository.findTop10InCity(Role.ROLE_CLEANER, city, TOP_TEN);

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

    /*
     * @Cacheable(value = "leaderboard_top", key = "'district:' + #state.toLowerCase() + '|' + #district.toLowerCase()")
     *
     * DISTRICT LEADERBOARD:
     *   Both parts of the (state, district) identity are required and both
     *   are in the key, so "Aurangabad" in Bihar and "Aurangabad" in
     *   Maharashtra can never share an entry, and one district's board
     *   aggregates every ULB inside it rather than splitting per city.
     *
     *   The inputs are trimmed but never title-cased: the frontend submits
     *   the directory's canonical spellings ("S.A.S Nagar"), and folding
     *   them ("S.a.s Nagar") both corrupted the display and missed rows
     *   stored under any other casing. Matching is LOWER(TRIM(...)) on both
     *   sides in the finder, so every stored casing variant is one district.
     *
     *   Same cache, same TTL, same eviction: RewardServiceImpl and the
     *   completion approval already evict every 'leaderboard_top' entry with
     *   allEntries = true, which covers district keys for free.
     */
    @Cacheable(value = "leaderboard_top",
            key = "'district:' + #state.toLowerCase() + '|' + #district.toLowerCase()")
    @Override
    public LeaderboardResponse getDistrictLeaderboard(String state, String district) {

        state = state.trim();
        district = district.trim();

        List<User> cleaners =
                userRepository.findTop10InDistrict(Role.ROLE_CLEANER, state, district, TOP_TEN);

        if (cleaners.isEmpty()) {

            return LeaderboardResponse.builder()
                    .leaderboardType(LeaderboardType.DISTRICT)
                    .location(district + ", " + state)
                    .message("No leaderboard data available for district '" + district + "' in " + state + ".")
                    .leaderboard(new ArrayList<>())
                    .build();
        }

        return buildLeaderboardResponse(
                cleaners,
                LeaderboardType.DISTRICT,
                district + ", " + state,
                "Top Cleaners in " + district + " district, " + state
        );
    }

    @Override
    public MyLeaderboardResponse getMyRanking() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        /*
         * The rank this cleaner would see on the National board.
         *
         * The board orders cleaners by reward points descending, breaking
         * ties by id ascending, and numbers its rows sequentially. This
         * cleaner's position in that ordering is therefore: every cleaner
         * with strictly more points, plus every equal-point cleaner whose
         * id orders them ahead, plus one. Counting only the strictly-higher
         * cleaners - the previous formula - answered the competition-style
         * rank instead, and the two diverged exactly when ties existed
         * (the board said 3 while My Standing said 2).
         */
        long cleanersRankedAbove =
                userRepository.countByRoleAndRewardPointsGreaterThan(
                        Role.ROLE_CLEANER,
                        cleaner.getRewardPoints()
                );

        long equalPointsRankedAhead =
                userRepository.countByRoleAndRewardPointsAndIdLessThan(
                        Role.ROLE_CLEANER,
                        cleaner.getRewardPoints(),
                        cleaner.getId()
                );

        // Current cleaner's rank - their actual national board position
        int rank = (int) (cleanersRankedAbove + equalPointsRankedAhead) + 1;

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