package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.RewardHistoryResponse;
import com.cleanbharat.wastemanagement.dto.RewardSummaryResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;

import java.util.List;

public interface RewardService {

    /**
     * Rewards the cleaner after successful cleanup.
     */
    void rewardCleaner(CleanupAssignment assignment);

    /**
     * Returns logged-in cleaner's reward summary.
     */
    RewardSummaryResponse getMyRewardSummary();

    /**
     * Returns logged-in cleaner's reward history.
     */
    List<RewardHistoryResponse> getMyRewardHistory();

}