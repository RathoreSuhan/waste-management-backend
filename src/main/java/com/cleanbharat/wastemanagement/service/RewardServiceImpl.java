package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.RewardHistoryResponse;
import com.cleanbharat.wastemanagement.dto.RewardSummaryResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.RewardHistory;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.RewardHistoryRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class RewardServiceImpl implements RewardService {

    // Reward history repository
    private final RewardHistoryRepository rewardHistoryRepository;

    // User repository
    private final UserRepository userRepository;

    // Base reward points
    private static final int BASE_REWARD_POINTS = 50;

    @Override
    public void rewardCleaner(CleanupAssignment assignment) {

        /*
         * Paid exactly once per cleanup.
         *
         * The municipality is the only source of a reward, and its sign-off
         * can legitimately be reached more than once: an officer may re-open
         * a completion, and a cleanup sent back for rework returns for a
         * second approval. Without this check the same cleanup would credit
         * the cleaner again on each pass, and the leaderboard - which reads
         * the cached total - would inherit the inflation.
         */
        if (rewardHistoryRepository.existsByAssignment(assignment)) {
            return; // Already rewarded, nothing further to credit
        }

        // Cleaner who completed the assignment
        User cleaner = assignment.getCleaner();

        /*
         * Create reward history entry.
         */
        RewardHistory rewardHistory = RewardHistory.builder()
                .cleaner(cleaner)
                .assignment(assignment)
                .points(BASE_REWARD_POINTS)
                .reason("Cleanup completed for Report #" + assignment.getReport().getId())
                .build();

        // Save reward history
        rewardHistoryRepository.save(rewardHistory);

        /*
         * Update cleaner's total reward points.
         *
         * We keep this cached value for fast
         * leaderboard queries.
         */
        cleaner.setRewardPoints(cleaner.getRewardPoints() + BASE_REWARD_POINTS);

        // Persist updated cleaner
        userRepository.save(cleaner);
    }

    /**
     * Returns the currently logged-in cleaner.
     */
    private User getLoggedInCleaner() {

        // Current authenticated user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User cleaner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Cleaner not found."));

        // Only cleaners can access reward APIs
        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can access reward information.");
        }
        return cleaner;
    }

    @Override
    public RewardSummaryResponse getMyRewardSummary() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return RewardSummaryResponse.builder()
                .cleanerName(cleaner.getName())                 // Cleaner name
                .totalRewardPoints(cleaner.getRewardPoints())   // Cached total points
                .build();
    }

    @Override
    public List<RewardHistoryResponse> getMyRewardHistory() {

        // Logged-in cleaner
        User cleaner = getLoggedInCleaner();

        return rewardHistoryRepository
                .findByCleanerOrderByCreatedAtDesc(cleaner)
                .stream()
                .map(this::mapToRewardHistoryResponse)
                .toList();
    }

    /**
     * Converts RewardHistory entity
     * into API response DTO.
     */
    private RewardHistoryResponse mapToRewardHistoryResponse(RewardHistory rewardHistory){
        return RewardHistoryResponse.builder()
                .points(rewardHistory.getPoints())          // Reward points
                .reason(rewardHistory.getReason())          // Reward reason
                .createdAt(rewardHistory.getCreatedAt())    // Earned date
                .build();
    }
}