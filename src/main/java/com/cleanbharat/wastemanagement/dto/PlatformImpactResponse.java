package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Required by Jackson to rebuild this DTO on a Redis cache HIT
@Builder
public class PlatformImpactResponse {

    // Total garbage sites reported by citizens
    private Long reportsFiled;

    // Cleanups officially completed (COMPLETED assignments / RESOLVED reports)
    private Long sitesCleared;

    // Total cleaners on the leaderboard
    private Long cleanersRanked;

    // Cleanups confirmed from before/after photographs
    private Long verifiedCleanups;
}
