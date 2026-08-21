package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.RewardHistory;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.RewardHistoryRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the reward payout rule.
 * Focus: points are credited only when the Municipal Corporation signs the completion off,
 * and the very same assignment can never be paid for twice.
 */
@ExtendWith(MockitoExtension.class)
class RewardServiceTest {

    private static final long ASSIGNMENT_ID = 41L;
    private static final long REPORT_ID = 7L;
    private static final int EXPECTED_POINTS = 50; // BASE_REWARD_POINTS in RewardServiceImpl

    @Mock private RewardHistoryRepository rewardHistoryRepository;
    @Mock private UserRepository userRepository; // cached rewardPoints on the cleaner row

    @InjectMocks private RewardServiceImpl rewardService;

    @Test
    void rewardIsCreditedOnceWhenMunicipalApprovalCompletesTheAssignment() {
        User cleaner = cleaner(5L, 120); // already holds some points from earlier work
        CleanupAssignment assignment = completedAssignment(cleaner);

        // Nothing has been paid for this assignment yet
        when(rewardHistoryRepository.existsByAssignment(assignment)).thenReturn(false);

        rewardService.rewardCleaner(assignment);

        // A ledger row is written so the payout is auditable and repeat-proof
        ArgumentCaptor<RewardHistory> saved = ArgumentCaptor.forClass(RewardHistory.class);
        verify(rewardHistoryRepository).save(saved.capture());
        assertSame(cleaner, saved.getValue().getCleaner());
        assertSame(assignment, saved.getValue().getAssignment());
        assertEquals(EXPECTED_POINTS, saved.getValue().getPoints());
        assertEquals("Cleanup completed for Report #" + REPORT_ID, saved.getValue().getReason());

        // Leaderboard reads the cached total, so it has to move by exactly the same amount
        assertEquals(120 + EXPECTED_POINTS, cleaner.getRewardPoints());
        verify(userRepository).save(cleaner);
    }

    @Test
    void rewardIsNotPaidTwiceForTheSameAssignment() {
        User cleaner = cleaner(5L, 170); // already paid for this assignment
        CleanupAssignment assignment = completedAssignment(cleaner);

        // The ledger already carries a row for this assignment
        when(rewardHistoryRepository.existsByAssignment(assignment)).thenReturn(true);

        rewardService.rewardCleaner(assignment); // e.g. a second sign-off click

        verify(rewardHistoryRepository, never()).save(any(RewardHistory.class));
        assertEquals(170, cleaner.getRewardPoints()); // total stays where it was
        verifyNoInteractions(userRepository);
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    private User cleaner(long id, int existingPoints) {
        return User.builder()
                .id(id)
                .name("Cleaner " + id)
                .email("cleaner.one@example.com")
                .role(Role.ROLE_CLEANER)
                .city("Mohali")
                .rewardPoints(existingPoints)
                .build();
    }

    /** An assignment the corporation has just approved, which is the only moment a reward is due. */
    private CleanupAssignment completedAssignment(User cleaner) {
        GarbageReport report = GarbageReport.builder()
                .id(REPORT_ID)
                .title("Garbage pile near market")
                .description("Mixed waste dumped on the footpath")
                .address("Phase 7 Market")
                .city("Mohali")
                .state("Punjab")
                .pincode("160055")
                .status(ReportStatus.RESOLVED)
                .build();

        return CleanupAssignment.builder()
                .id(ASSIGNMENT_ID)
                .report(report)
                .cleaner(cleaner)
                .status(AssignmentStatus.COMPLETED)
                .build();
    }
}