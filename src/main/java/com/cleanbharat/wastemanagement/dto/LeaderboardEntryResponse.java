package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.BadgeType;
import lombok.*;

/**
 * Represents one cleaner entry
 * displayed on the leaderboard.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardEntryResponse {

    // Current leaderboard rank
    private Integer rank;

    // Cleaner name
    private String cleanerName;

    // Total reward points
    private Integer rewardPoints;

    // Successfully completed cleanups
    private Long completedCleanups;

    // AI verified cleanups
    private Long aiVerifiedCleanups;

    // Badge earned
    private BadgeType badge;
}