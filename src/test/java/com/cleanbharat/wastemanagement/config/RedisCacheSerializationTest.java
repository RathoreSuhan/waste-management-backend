package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.dto.DashboardAnalyticsResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardEntryResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalDashboardStatsResponse;
import com.cleanbharat.wastemanagement.dto.admin.DashboardResponse;
import com.cleanbharat.wastemanagement.enums.BadgeType;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips the DTOs that live in Redis through the exact serializer
 * RedisConfig uses in production.
 *
 * Why this matters: a cache MISS always works (the DTO is built by the
 * service itself), but a cache HIT must be deserialized by Jackson from
 * JSON - and that is exactly what broke the homepage "Platform Impact"
 * section before: DashboardAnalyticsResponse had no default constructor,
 * so Jackson could not rebuild it and every cache HIT threw an exception.
 */
class RedisCacheSerializationTest {

    // Same serializer instance the CacheManager stores cache values with
    private final GenericJackson2JsonRedisSerializer serializer =
            RedisConfig.cacheValueSerializer();

    @Test
    void dashboardAnalyticsResponseSurvivesCacheRoundTrip() {

        DashboardAnalyticsResponse original = DashboardAnalyticsResponse.builder()
                .totalReports(42L)
                .totalVotes(120L)
                .totalComments(35L)
                .totalReplies(18L)
                .averageUrgencyScore(3.75)
                .averageEngagementScore(6.4)
                .mostTrendingReportId(7L)
                .build();

        // Simulate: service returns the DTO -> Spring serializes it into Redis
        byte[] cached = serializer.serialize(original);

        // Simulate: next request is a cache HIT -> Spring deserializes it back
        Object restored = serializer.deserialize(cached);

        assertThat(restored).isInstanceOf(DashboardAnalyticsResponse.class);

        DashboardAnalyticsResponse dto = (DashboardAnalyticsResponse) restored;
        assertThat(dto.getTotalReports()).isEqualTo(42L);
        assertThat(dto.getTotalVotes()).isEqualTo(120L);
        assertThat(dto.getTotalComments()).isEqualTo(35L);
        assertThat(dto.getTotalReplies()).isEqualTo(18L);
        assertThat(dto.getAverageUrgencyScore()).isEqualTo(3.75);
        assertThat(dto.getAverageEngagementScore()).isEqualTo(6.4);
        assertThat(dto.getMostTrendingReportId()).isEqualTo(7L);
    }

    @Test
    void leaderboardResponseSurvivesCacheRoundTrip() {

        // Production builds the entries as a plain ArrayList (LeaderboardServiceImpl)
        List<LeaderboardEntryResponse> entries = new ArrayList<>();
        entries.add(LeaderboardEntryResponse.builder()
                .rank(1)
                .cleanerName("Ravi Kumar")
                .rewardPoints(520)
                .completedCleanups(26L)
                .aiVerifiedCleanups(26L)
                .badge(BadgeType.GOLD)
                .build());

        LeaderboardResponse original = LeaderboardResponse.builder()
                .leaderboardType(LeaderboardType.NATIONAL)
                .location("India")
                .message("Top cleaners across the country")
                .leaderboard(entries)
                .build();

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(LeaderboardResponse.class);

        LeaderboardResponse dto = (LeaderboardResponse) restored;
        assertThat(dto.getLeaderboardType()).isEqualTo(LeaderboardType.NATIONAL);
        assertThat(dto.getLocation()).isEqualTo("India");
        assertThat(dto.getLeaderboard()).hasSize(1);
        assertThat(dto.getLeaderboard().get(0).getCleanerName()).isEqualTo("Ravi Kumar");
        assertThat(dto.getLeaderboard().get(0).getBadge()).isEqualTo(BadgeType.GOLD);
    }

    @Test
    void adminDashboardResponseSurvivesCacheRoundTrip() {

        // The figures an admin actually sees, taken from a live dashboard
        DashboardResponse original = DashboardResponse.builder()
                .totalUsers(23L)
                .totalCitizens(10L)
                .totalCleaners(10L)
                .totalAdmins(3L)
                .totalReports(7L)
                .pendingReports(2L)
                .completedReports(4L)
                .verifiedCleanups(4L)
                .totalComments(24L)
                .totalVotes(5L)
                .topCleaner("cleaner2")
                .build();

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(DashboardResponse.class);

        DashboardResponse dto = (DashboardResponse) restored;
        assertThat(dto.getTotalUsers()).isEqualTo(23L);
        assertThat(dto.getTotalCitizens()).isEqualTo(10L);
        assertThat(dto.getTotalCleaners()).isEqualTo(10L);
        assertThat(dto.getTotalAdmins()).isEqualTo(3L);
        assertThat(dto.getTotalReports()).isEqualTo(7L);
        assertThat(dto.getPendingReports()).isEqualTo(2L);
        assertThat(dto.getCompletedReports()).isEqualTo(4L);
        assertThat(dto.getVerifiedCleanups()).isEqualTo(4L);
        assertThat(dto.getTotalComments()).isEqualTo(24L);
        assertThat(dto.getTotalVotes()).isEqualTo(5L);
        assertThat(dto.getTopCleaner()).isEqualTo("cleaner2");
    }

    @Test
    void municipalDashboardStatsResponseSurvivesCacheRoundTrip() {

        MunicipalDashboardStatsResponse original = MunicipalDashboardStatsResponse.builder()
                .corporationName("Municipal Corporation SAS Nagar Mohali")
                .city("Mohali")
                .relevantReports(3)
                .pendingProposals(0)
                .activeCleanups(1)
                .completionReviews(0)
                .completedCleanups(1)
                .build();

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(MunicipalDashboardStatsResponse.class);

        MunicipalDashboardStatsResponse dto = (MunicipalDashboardStatsResponse) restored;
        assertThat(dto.getCorporationName()).isEqualTo("Municipal Corporation SAS Nagar Mohali");
        assertThat(dto.getCity()).isEqualTo("Mohali");
        assertThat(dto.getRelevantReports()).isEqualTo(3);
        assertThat(dto.getPendingProposals()).isZero();
        assertThat(dto.getActiveCleanups()).isEqualTo(1);
        assertThat(dto.getCompletionReviews()).isZero();
        assertThat(dto.getCompletedCleanups()).isEqualTo(1);
    }

    /*
     * The municipal overview is cached under a key built by a SpEL string
     * rather than from a method argument, because getDashboardStats() has no
     * arguments - it resolves the corporation from the token itself.
     *
     * A SpEL string is only parsed when a cached method is first called, so a
     * typo in it would surface as a production failure on a cache read and
     * nowhere earlier. This evaluates the very same constant the annotation
     * uses, so a broken expression fails the build instead.
     */
    @Test
    void municipalCacheKeyExpressionResolvesToTheLowercasedPrincipal() {

        // Deliberately mixed case: two officers of one corporation typing
        // their email differently must not occupy two cache entries
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("SASNagar@MC.Gov.in", null, List.of())
        );

        Object key = new SpelExpressionParser()
                .parseExpression(RedisConfig.MUNICIPAL_STATS_KEY_EXPRESSION)
                .getValue();

        assertThat(key).isEqualTo("sasnagar@mc.gov.in");
    }

    @AfterEach
    void clearAuthentication() {
        // The context is a thread local, so it would leak into the next test
        SecurityContextHolder.clearContext();
    }
}
