package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.dto.DashboardAnalyticsResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardEntryResponse;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.enums.BadgeType;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

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
}
