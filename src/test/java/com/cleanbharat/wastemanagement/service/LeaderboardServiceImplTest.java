package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the leaderboard scopes.
 *
 * The district board is the location refactor's addition (v6-1): it must
 * aggregate every ULB inside one district, keep identically named districts
 * of different states separate, and leave the national/state/city boards
 * byte-identical to what they have always been.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private CleanupAssignmentRepository cleanupAssignmentRepository;

    @InjectMocks private LeaderboardServiceImpl leaderboardService;

    @BeforeEach
    void stubCompletedCounts() {
        // Every ranked cleaner has one completed cleanup; lenient because the
        // empty-board tests never reach the counting
        lenient().when(cleanupAssignmentRepository.countByCleanerAndStatus(
                        any(User.class), eq(AssignmentStatus.COMPLETED)))
                .thenReturn(1L);
    }

    private User cleaner(long id, String name, int points, String state, String city, String district) {
        return User.builder()
                .id(id)
                .name(name)
                .email("cleaner" + id + "@example.com")
                .role(Role.ROLE_CLEANER)
                .state(state)
                .city(city)
                .district(district)
                .rewardPoints(points)
                .build();
    }

    @Test
    void theNationalBoardIsUnchangedInShapeAndOrder() {
        User gold = cleaner(1L, "Gold Cleaner", 500, "Bihar", "Gaya", "Gaya");
        User silver = cleaner(2L, "Silver Cleaner", 250, "Punjab", "Mohali", "S.a.s Nagar");
        when(userRepository.findTop10ByRoleOrderByRewardPointsDesc(Role.ROLE_CLEANER))
                .thenReturn(List.of(gold, silver));

        LeaderboardResponse board = leaderboardService.getPublicLeaderboard();

        assertEquals(LeaderboardType.NATIONAL, board.getLeaderboardType());
        assertEquals(2, board.getLeaderboard().size());
        assertEquals(1, board.getLeaderboard().get(0).getRank());
        assertEquals("Gold Cleaner", board.getLeaderboard().get(0).getCleanerName());
        assertEquals(1, board.getLeaderboard().get(0).getCompletedCleanups());
    }

    @Test
    void theStateAndCityBoardsKeepTheirExistingBehavior() {
        User mohaliCleaner = cleaner(2L, "Mohali Cleaner", 250, "Punjab", "S.a.s.nagar - Mohali", "S.a.s Nagar");
        when(userRepository.findTop10ByRoleAndStateOrderByRewardPointsDesc(Role.ROLE_CLEANER, "Punjab"))
                .thenReturn(List.of(mohaliCleaner));
        when(userRepository.findTop10ByRoleAndCityOrderByRewardPointsDesc(Role.ROLE_CLEANER, "S.a.s.nagar - Mohali"))
                .thenReturn(List.of(mohaliCleaner));

        // Free-typed inputs normalise the same way as at registration; the
        // stored "S.a.s.nagar - Mohali" is one word, so the typed input must
        // carry the same acronym folding (pre-existing board semantics)
        LeaderboardResponse state = leaderboardService.getStateLeaderboard("punjab");
        LeaderboardResponse city = leaderboardService.getCityLeaderboard("S.a.s.nagar - Mohali");

        assertEquals(LeaderboardType.STATE, state.getLeaderboardType());
        assertEquals(1, state.getLeaderboard().size());
        assertEquals(LeaderboardType.CITY, city.getLeaderboardType());
        assertEquals(1, city.getLeaderboard().size());
    }

    @Test
    void theDistrictBoardAggregatesEveryUlbInsideOneDistrict() {
        // Two cleaners from different ULBs of the same district: both belong
        // on one district board, which is exactly what the city board split
        User gayaCity = cleaner(1L, "Gaya City Cleaner", 300, "Bihar", "Gaya", "Gaya");
        User bodhGaya = cleaner(2L, "Bodh Gaya Cleaner", 120, "Bihar", "Bodh Gaya", "Gaya");
        when(userRepository.findTop10ByRoleAndStateAndDistrictOrderByRewardPointsDesc(
                Role.ROLE_CLEANER, "Bihar", "Gaya"))
                .thenReturn(List.of(gayaCity, bodhGaya));

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("Bihar", "Gaya");

        assertEquals(LeaderboardType.DISTRICT, board.getLeaderboardType());
        assertEquals(2, board.getLeaderboard().size());
        assertEquals(1, board.getLeaderboard().get(0).getRank());
        assertEquals(300, board.getLeaderboard().get(0).getRewardPoints());
        assertTrue(board.getMessage().contains("Gaya district, Bihar"), board.getMessage());
    }

    @Test
    void identicalDistrictNamesInDifferentStatesNeverShareABoard() {
        // Aurangabad exists in both Bihar and Maharashtra: the repository is
        // asked for the (state, district) pair, so the two queries differ and
        // one state's cleaners can never appear on the other's board
        User biharCleaner = cleaner(1L, "Bihar Cleaner", 90, "Bihar", "Aurangabad", "Aurangabad");
        when(userRepository.findTop10ByRoleAndStateAndDistrictOrderByRewardPointsDesc(
                Role.ROLE_CLEANER, "Bihar", "Aurangabad"))
                .thenReturn(List.of(biharCleaner));

        LeaderboardResponse biharBoard = leaderboardService.getDistrictLeaderboard("Bihar", "Aurangabad");

        verify(userRepository).findTop10ByRoleAndStateAndDistrictOrderByRewardPointsDesc(
                Role.ROLE_CLEANER, "Bihar", "Aurangabad");
        assertEquals(1, biharBoard.getLeaderboard().size());
        assertEquals("Bihar Cleaner", biharBoard.getLeaderboard().get(0).getCleanerName());
    }

    @Test
    void anEmptyDistrictBoardIsAnsweredWithAMessageNotAnError() {
        when(userRepository.findTop10ByRoleAndStateAndDistrictOrderByRewardPointsDesc(
                Role.ROLE_CLEANER, "Bihar", "Gaya"))
                .thenReturn(List.of());

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("bihar", "gaya");

        assertEquals(LeaderboardType.DISTRICT, board.getLeaderboardType());
        assertTrue(board.getLeaderboard().isEmpty());
        assertTrue(board.getMessage().contains("No leaderboard data"), board.getMessage());
    }
}
