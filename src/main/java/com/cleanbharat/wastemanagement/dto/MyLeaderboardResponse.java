package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.BadgeType;
import lombok.*;

/**
 * Response returned for
 * logged-in cleaner's
 * personal leaderboard details.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyLeaderboardResponse {

    // Current rank
    private Integer rank;

    // Cleaner name
    private String cleanerName;

    // Badge earned
    private BadgeType badge;

    // Total reward points
    private Integer rewardPoints;

    // Completed cleanups
    private Long completedCleanups;

    // AI verified cleanups
    private Long aiVerifiedCleanups;

    // Points needed for next badge
    private Integer pointsToNextBadge;
}