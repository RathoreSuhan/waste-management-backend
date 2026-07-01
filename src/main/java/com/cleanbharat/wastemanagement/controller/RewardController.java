package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.RewardHistoryResponse;
import com.cleanbharat.wastemanagement.dto.RewardSummaryResponse;
import com.cleanbharat.wastemanagement.service.RewardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/rewards")
@RequiredArgsConstructor
public class RewardController {

    // Reward service
    private final RewardService rewardService;

    /**
     * Returns logged-in cleaner's reward summary.
     */
    @GetMapping("/me")
    public ResponseEntity<RewardSummaryResponse> getMyRewardSummary() {
        return ResponseEntity.ok(rewardService.getMyRewardSummary());
    }

    /**
     * Returns logged-in cleaner's reward history.
     */
    @GetMapping("/history")
    public ResponseEntity<List<RewardHistoryResponse>> getMyRewardHistory() {
        return ResponseEntity.ok(rewardService.getMyRewardHistory());
    }

}