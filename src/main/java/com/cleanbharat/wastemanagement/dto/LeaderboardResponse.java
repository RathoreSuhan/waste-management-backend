package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import lombok.*;

import java.util.List;

/**
 * Response returned for
 * leaderboard APIs.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaderboardResponse {

    // National / State / City
    private LeaderboardType leaderboardType;

    // India / Bihar / Patna
    private String location;

    // Response message
    private String message;

    // Top cleaner entries
    private List<LeaderboardEntryResponse> leaderboard;
}