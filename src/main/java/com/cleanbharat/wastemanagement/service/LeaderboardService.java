package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MyLeaderboardResponse;

/**
 * Service responsible for
 * generating leaderboard data.
 */
public interface LeaderboardService {

    /**
     * Returns the public leaderboard.

     * Only the Top 10 cleaners
     * are returned.
     */
    LeaderboardResponse getPublicLeaderboard();

    /**
     * Returns the logged-in
     * cleaner's personal ranking.
     */
    MyLeaderboardResponse getMyRanking();

    /**
     * Returns Top 10 cleaners
     * of a particular state.
     */
    LeaderboardResponse getStateLeaderboard(String state);

    /**
     * Returns Top 10 cleaners
     * of a particular city.
     */
    LeaderboardResponse getCityLeaderboard(String city);

    /**
     * Returns Top 10 cleaners of a district, aggregating every ULB inside
     * it. Both the state and the district are required: two different states
     * can name a district the same way, so the (state, district) pair is
     * what identifies one board.
     */
    LeaderboardResponse getDistrictLeaderboard(String state, String district);
}