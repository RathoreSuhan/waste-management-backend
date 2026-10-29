package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.LocationDataUnavailableException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the leaderboard scopes.
 *
 * Matching semantics live in the repository queries (see
 * UserRepositoryLeaderboardTest for the real-JPQL proof); these tests pin
 * the service contract on top of it: submitted names are trimmed but never
 * title-cased, so "S.A.S Nagar" reaches the query and the response exactly
 * as the directory spells it; a submitted official ULB name is resolved to
 * its code and read by that identity; anything the directory cannot
 * resolve falls back to the legacy city search.
 */
@ExtendWith(MockitoExtension.class)
class LeaderboardServiceImplTest {

    @Mock private UserRepository userRepository;
    @Mock private CleanupAssignmentRepository cleanupAssignmentRepository;
    @Mock private IndianLocationService indianLocationService;

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

    private IndianLocationService.NameMatch ulb(String ulbName, String ulbCode) {
        return new IndianLocationService.NameMatch(
                new IndianLocationService.ResolvedLocation("Punjab", "S.A.S Nagar", ulbName, ulbCode),
                null);
    }

    // ------------------------------------------------------------ national

    @Test
    void theNationalBoardIsUnchangedInShapeAndOrder() {
        User gold = cleaner(1L, "Gold Cleaner", 500, "Bihar", "Gaya", "Gaya");
        User silver = cleaner(2L, "Silver Cleaner", 250, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10ByRoleOrderByRewardPointsDesc(Role.ROLE_CLEANER))
                .thenReturn(List.of(gold, silver));

        LeaderboardResponse board = leaderboardService.getPublicLeaderboard();

        assertEquals(LeaderboardType.NATIONAL, board.getLeaderboardType());
        assertEquals(2, board.getLeaderboard().size());
        assertEquals(1, board.getLeaderboard().get(0).getRank());
        assertEquals("Gold Cleaner", board.getLeaderboard().get(0).getCleanerName());
        assertEquals(1, board.getLeaderboard().get(0).getCompletedCleanups());
    }

    // --------------------------------------------------------------- state

    @Test
    void theStateBoardTrimsItsInputAndKeepsTheSubmittedSpelling() {
        User punjabCleaner = cleaner(2L, "Mohali Cleaner", 250, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10InState(eq(Role.ROLE_CLEANER), eq("Punjab"), any(Pageable.class)))
                .thenReturn(List.of(punjabCleaner));

        LeaderboardResponse board = leaderboardService.getStateLeaderboard("  Punjab  ");

        assertEquals(LeaderboardType.STATE, board.getLeaderboardType());
        assertEquals(1, board.getLeaderboard().size());
        assertEquals("Punjab", board.getLocation());
        assertTrue(board.getMessage().contains("Top Cleaners in Punjab"), board.getMessage());
    }

    // ------------------------------------------------------------ district

    @Test
    void theDistrictBoardAggregatesEveryUlbInsideOneDistrict() {
        User gayaCity = cleaner(1L, "Gaya City Cleaner", 300, "Bihar", "Gaya", "Gaya");
        User bodhGaya = cleaner(2L, "Bodh Gaya Cleaner", 120, "Bihar", "Bodh Gaya", "Gaya");
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Bihar"), eq("Gaya"), any(Pageable.class)))
                .thenReturn(List.of(gayaCity, bodhGaya));

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("Bihar", "Gaya");

        assertEquals(LeaderboardType.DISTRICT, board.getLeaderboardType());
        assertEquals(2, board.getLeaderboard().size());
        assertEquals(1, board.getLeaderboard().get(0).getRank());
        assertEquals(300, board.getLeaderboard().get(0).getRewardPoints());
        assertTrue(board.getMessage().contains("Gaya district, Bihar"), board.getMessage());
    }

    @Test
    void theDistrictBoardKeepsTheCanonicalDistrictNameExactlyAsSubmitted() {
        // The reported failure: the acronym district was folded into
        // "S.a.s Nagar" before the query AND in the message. Both must now
        // carry the canonical directory spelling the frontend submits.
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class)))
                .thenReturn(List.of());

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("Punjab", "S.A.S Nagar");

        assertTrue(board.getMessage().contains("'S.A.S Nagar'"), board.getMessage());
        assertFalse(board.getMessage().contains("S.a.s Nagar"), board.getMessage());
        assertEquals("S.A.S Nagar, Punjab", board.getLocation());
    }

    @Test
    void theDistrictBoardDisplaysTheCanonicalPairWhenCleanersAreFound() {
        User sasCleaner = cleaner(3L, "Mohali Cleaner", 50, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class)))
                .thenReturn(List.of(sasCleaner));

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("Punjab", "S.A.S Nagar");

        assertEquals("S.A.S Nagar, Punjab", board.getLocation());
        assertTrue(board.getMessage().contains("Top Cleaners in S.A.S Nagar district, Punjab"),
                board.getMessage());
    }

    @Test
    void districtInputsAreTrimmedBeforeTheyReachTheFinder() {
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class)))
                .thenReturn(List.of());

        leaderboardService.getDistrictLeaderboard("  Punjab ", "\tS.A.S Nagar\n");

        verify(userRepository).findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class));
    }

    @Test
    void aZeroPointsCleanerIsNotExcludedFromTheDistrictBoard() {
        User zeroPoints = cleaner(4L, "New Cleaner", 0, "Punjab", "Mohali", "S.A.S Nagar");
        User veteran = cleaner(5L, "Veteran Cleaner", 100, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class)))
                .thenReturn(List.of(veteran, zeroPoints));

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("Punjab", "S.A.S Nagar");

        assertEquals(2, board.getLeaderboard().size());
        assertEquals("New Cleaner", board.getLeaderboard().get(1).getCleanerName());
        assertEquals(0, board.getLeaderboard().get(1).getRewardPoints());
    }

    @Test
    void identicalDistrictNamesInDifferentStatesNeverShareABoard() {
        // Aurangabad exists in both Bihar and Maharashtra: the finder is
        // asked for the (state, district) pair, so the two queries differ
        // and one state's cleaners can never appear on the other's board
        User biharCleaner = cleaner(1L, "Bihar Cleaner", 90, "Bihar", "Aurangabad", "Aurangabad");
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Bihar"), eq("Aurangabad"), any(Pageable.class)))
                .thenReturn(List.of(biharCleaner));

        LeaderboardResponse biharBoard = leaderboardService.getDistrictLeaderboard("Bihar", "Aurangabad");

        verify(userRepository).findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Bihar"), eq("Aurangabad"), any(Pageable.class));
        assertEquals(1, biharBoard.getLeaderboard().size());
        assertEquals("Bihar Cleaner", biharBoard.getLeaderboard().get(0).getCleanerName());
    }

    @Test
    void anEmptyDistrictBoardIsAnsweredWithAMessageNotAnError() {
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("bihar"), eq("gaya"), any(Pageable.class)))
                .thenReturn(List.of());

        LeaderboardResponse board = leaderboardService.getDistrictLeaderboard("bihar", "gaya");

        assertEquals(LeaderboardType.DISTRICT, board.getLeaderboardType());
        assertTrue(board.getLeaderboard().isEmpty());
        assertTrue(board.getMessage().contains("No leaderboard data"), board.getMessage());
    }

    // ---------------------------------------------------------------- city

    @Test
    void anOfficialUlbNameIsResolvedThroughTheDirectoryAndReadByItsCode() {
        when(indianLocationService.matchByCityName("S.A.S.Nagar - Mohali"))
                .thenReturn(ulb("S.A.S.Nagar - Mohali", "248325"));
        User legacyCityCleaner = cleaner(6L, "Legacy City Cleaner", 50, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10ByUlbCode(eq(Role.ROLE_CLEANER), eq("248325"), any(Pageable.class)))
                .thenReturn(List.of(legacyCityCleaner));

        LeaderboardResponse board = leaderboardService.getCityLeaderboard("S.A.S.Nagar - Mohali");

        // The board is read by ULB identity, so the stored legacy city value
        // ("Mohali") never has to match the official name
        verify(userRepository).findTop10ByUlbCode(
                eq(Role.ROLE_CLEANER), eq("248325"), any(Pageable.class));
        verify(userRepository, org.mockito.Mockito.never())
                .findTop10InCity(any(), any(), any(Pageable.class));
        assertEquals(LeaderboardType.CITY, board.getLeaderboardType());
        assertEquals("S.A.S.Nagar - Mohali", board.getLocation());
        assertTrue(board.getMessage().contains("Top Cleaners in S.A.S.Nagar - Mohali"),
                board.getMessage());
        assertEquals(1, board.getLeaderboard().size());
    }

    @Test
    void anOfficialUlbNameWithNoCleanersAnswersWithTheCanonicalName() {
        when(indianLocationService.matchByCityName("S.A.S.Nagar - Mohali"))
                .thenReturn(ulb("S.A.S.Nagar - Mohali", "248325"));
        when(userRepository.findTop10ByUlbCode(eq(Role.ROLE_CLEANER), eq("248325"), any(Pageable.class)))
                .thenReturn(List.of());

        LeaderboardResponse board = leaderboardService.getCityLeaderboard("S.A.S.Nagar - Mohali");

        assertTrue(board.getLeaderboard().isEmpty());
        assertEquals("S.A.S.Nagar - Mohali", board.getLocation());
        assertTrue(board.getMessage().contains("'S.A.S.Nagar - Mohali'"), board.getMessage());
    }

    @Test
    void anUnresolvedCityNameFallsBackToTheLegacyCitySearch() {
        when(indianLocationService.matchByCityName("Mohali"))
                .thenReturn(new IndianLocationService.NameMatch(
                        null, "no directory ULB named 'mohali'"));
        User legacyCleaner = cleaner(7L, "Mohali Cleaner", 100, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10InCity(eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class)))
                .thenReturn(List.of(legacyCleaner));

        LeaderboardResponse board = leaderboardService.getCityLeaderboard("Mohali");

        verify(userRepository).findTop10InCity(
                eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class));
        verify(userRepository, org.mockito.Mockito.never())
                .findTop10ByUlbCode(any(), any(), any(Pageable.class));
        assertEquals("Mohali", board.getLocation());
        assertEquals(1, board.getLeaderboard().size());
    }

    @Test
    void anAmbiguousUlbNameAlsoFallsBackToTheLegacyCitySearch() {
        when(indianLocationService.matchByCityName("Santipur"))
                .thenReturn(new IndianLocationService.NameMatch(
                        null, "the name 'santipur' is shared by 2 directory ULBs"));
        when(userRepository.findTop10InCity(eq(Role.ROLE_CLEANER), eq("Santipur"), any(Pageable.class)))
                .thenReturn(List.of());

        leaderboardService.getCityLeaderboard("Santipur");

        verify(userRepository).findTop10InCity(
                eq(Role.ROLE_CLEANER), eq("Santipur"), any(Pageable.class));
    }

    @Test
    void anUnavailableDirectoryDegradesTheCityBoardToItsLegacySearch() {
        when(indianLocationService.matchByCityName("Mohali"))
                .thenThrow(new LocationDataUnavailableException("Location data is temporarily unavailable"));
        User legacyCleaner = cleaner(7L, "Mohali Cleaner", 100, "Punjab", "Mohali", "S.A.S Nagar");
        when(userRepository.findTop10InCity(eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class)))
                .thenReturn(List.of(legacyCleaner));

        LeaderboardResponse board = leaderboardService.getCityLeaderboard("Mohali");

        assertEquals(LeaderboardType.CITY, board.getLeaderboardType());
        assertEquals(1, board.getLeaderboard().size());
        verify(userRepository).findTop10InCity(
                eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class));
    }

    @Test
    void cityInputsAreTrimmedBeforeTheyReachTheDirectoryOrTheFinder() {
        when(indianLocationService.matchByCityName("Mohali"))
                .thenReturn(new IndianLocationService.NameMatch(null, "no match"));
        when(userRepository.findTop10InCity(eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class)))
                .thenReturn(List.of());

        leaderboardService.getCityLeaderboard("  Mohali  ");

        verify(indianLocationService).matchByCityName("Mohali");
        verify(userRepository).findTop10InCity(
                eq(Role.ROLE_CLEANER), eq("Mohali"), any(Pageable.class));
    }
}
