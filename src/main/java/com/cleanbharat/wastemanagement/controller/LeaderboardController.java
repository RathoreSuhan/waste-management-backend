package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MyLeaderboardResponse;
import com.cleanbharat.wastemanagement.service.LeaderboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST APIs for Leaderboard Module.
 */
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    // Leaderboard business logic
    private final LeaderboardService leaderboardService;

    /**
     * Returns the National Top 10 leaderboard.
     * Accessible by anyone.
     */
    @GetMapping
    public LeaderboardResponse getPublicLeaderboard() {
        return leaderboardService.getPublicLeaderboard();
    }

    /**
     * Returns Top 10 cleaners of the requested state.
     * Accessible by anyone.
     */
    @GetMapping("/state/{state}")
    public LeaderboardResponse getStateLeaderboard(
            @PathVariable String state
    ) {
        return leaderboardService.getStateLeaderboard(state);
    }

    /**
     * Returns Top 10 cleaners of the requested city.
     * Accessible by anyone.
     */
    @GetMapping("/city/{city}")
    public LeaderboardResponse getCityLeaderboard(
            @PathVariable String city
    ) {
        return leaderboardService.getCityLeaderboard(city);
    }

    /**
     * Returns logged-in cleaner's personal leaderboard details.
     * Accessible only after login.
     */
    @GetMapping("/me")
    public MyLeaderboardResponse getMyRanking() {
        return leaderboardService.getMyRanking();
    }
}