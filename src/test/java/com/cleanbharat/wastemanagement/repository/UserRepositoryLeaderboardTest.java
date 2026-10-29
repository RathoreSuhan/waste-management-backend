package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the leaderboard finders' real JPQL against an in-memory database,
 * because the matching semantics live in the queries themselves: a
 * district board must find rows stored under ANY casing of the canonical
 * name ("S.A.S Nagar", "S.a.s Nagar"), tolerate surrounding whitespace on
 * either side, keep identically named districts of different states apart,
 * and let the ULB board find rows whose legacy city spelling differs from
 * the official ULB name.
 *
 * The fixtures reproduce the reported production shape - Punjab rows with
 * district 'S.A.S Nagar', city 'Mohali', ULB 248325 - with synthetic
 * personal data only.
 */
@DataJpaTest
class UserRepositoryLeaderboardTest {

    private static final PageRequest TOP_TEN = PageRequest.of(0, 10);

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void seedCleaners() {

        // The reported production shape: canonical directory casing stored
        save("Sas Nagar Cleaner", 50, "Punjab", "S.A.S Nagar", "Mohali", "248325");
        // The same district under the registration-folded casing, with no
        // reconciled ULB code (legacy row): found by name, never by code
        save("Folded Casing Cleaner", 40, "Punjab", "S.a.s Nagar", "Mohali", null);
        // A zero-points cleaner is still eligible to appear
        save("Zero Points Cleaner", 0, "Punjab", "S.A.S Nagar", "Mohali", "248325");
        // Stored values carrying surrounding whitespace must still match
        save("Padded Values Cleaner", 30, "  Punjab", "S.A.S Nagar  ", " Mohali ", "248325");
        // Another Punjab district, excluded from the S.A.S Nagar board and ULB
        save("Ludhiana Cleaner", 60, "Punjab", "Ludhiana", "Ludhiana", "248100");
        // The working regression case
        save("Gaya Cleaner", 50, "Bihar", "Gaya", "Gaya", "249680");
        // Identically named districts in two different states
        save("Bihar Aurangabad Cleaner", 20, "Bihar", "Aurangabad", "Aurangabad", null);
        save("Maharashtra Aurangabad Cleaner", 10, "Maharashtra", "Aurangabad", "Aurangabad", null);
    }

    private User save(String name, int points, String state, String district, String city, String ulbCode) {
        return userRepository.save(User.builder()
                .name(name)
                .email(name.toLowerCase().replace(' ', '.') + "@example.com")
                .password("test-password")
                .role(Role.ROLE_CLEANER)
                .state(state)
                .city(city)
                .district(district)
                .ulbCode(ulbCode)
                .rewardPoints(points)
                .build());
    }

    private List<String> names(List<User> cleaners) {
        return cleaners.stream().map(User::getName).collect(Collectors.toList());
    }

    @Test
    void theDistrictBoardFindsEveryStoredCasingOfSasNagar() {
        List<User> board = userRepository.findTop10InDistrict(
                Role.ROLE_CLEANER, "Punjab", "S.A.S Nagar", TOP_TEN);

        // Canonical, folded, zero-points and whitespace-padded rows all belong,
        // ordered by reward points descending
        assertEquals(
                List.of("Sas Nagar Cleaner", "Folded Casing Cleaner", "Padded Values Cleaner", "Zero Points Cleaner"),
                names(board));
    }

    @Test
    void aLowercaseDistrictInputFindsTheSameRows() {
        List<User> board = userRepository.findTop10InDistrict(
                Role.ROLE_CLEANER, "punjab", "s.a.s nagar", TOP_TEN);

        assertTrue(names(board).contains("Sas Nagar Cleaner"));
        assertTrue(names(board).contains("Folded Casing Cleaner"));
        assertEquals(4, board.size());
    }

    @Test
    void surroundingWhitespaceOnEitherSideIsTolerated() {
        // Padded INPUT against clean stored values. JPQL TRIM strips spaces
        // (the realistic padding), and the service String-trims any input
        // before it reaches the query, so other whitespace never arrives.
        List<User> paddedInput = userRepository.findTop10InDistrict(
                Role.ROLE_CLEANER, "  Punjab  ", " S.A.S Nagar ", TOP_TEN);
        assertEquals(4, paddedInput.size());

        // ...and clean input against PADDED STORED VALUES (the row seeded
        // with '  Punjab' / 'S.A.S Nagar  ' must not be lost)
        assertTrue(names(paddedInput).contains("Padded Values Cleaner"));
    }

    @Test
    void biharGayaKeepsWorking() {
        List<User> board = userRepository.findTop10InDistrict(
                Role.ROLE_CLEANER, "Bihar", "Gaya", TOP_TEN);

        assertEquals(List.of("Gaya Cleaner"), names(board));
    }

    @Test
    void identicallyNamedDistrictsNeverShareABoard() {
        assertEquals(
                List.of("Bihar Aurangabad Cleaner"),
                names(userRepository.findTop10InDistrict(
                        Role.ROLE_CLEANER, "Bihar", "Aurangabad", TOP_TEN)));

        assertEquals(
                List.of("Maharashtra Aurangabad Cleaner"),
                names(userRepository.findTop10InDistrict(
                        Role.ROLE_CLEANER, "Maharashtra", "Aurangabad", TOP_TEN)));
    }

    @Test
    void theUlbBoardFindsRowsWhoseLegacyCityDiffersFromTheOfficialName() {
        List<User> board = userRepository.findTop10ByUlbCode(
                Role.ROLE_CLEANER, "248325", TOP_TEN);

        // Rows with ulb_code 248325 regardless of their stored city value...
        assertTrue(names(board).contains("Sas Nagar Cleaner"));
        assertTrue(names(board).contains("Zero Points Cleaner"));
        // ...excluding another ULB's cleaners and the un-reconciled row
        assertEquals(3, board.size());
        assertTrue(names(board).contains("Padded Values Cleaner"));
        assertTrue(!names(board).contains("Ludhiana Cleaner"));
        assertTrue(!names(board).contains("Folded Casing Cleaner"));
    }

    @Test
    void theLegacyCityBoardStillFindsItsRows() {
        assertEquals(
                List.of("Sas Nagar Cleaner", "Folded Casing Cleaner",
                        "Padded Values Cleaner", "Zero Points Cleaner"),
                names(userRepository.findTop10InCity(
                        Role.ROLE_CLEANER, "Mohali", TOP_TEN)));

        // Casing and whitespace on the typed city are tolerated too
        assertEquals(4, userRepository.findTop10InCity(
                Role.ROLE_CLEANER, "  mohali  ", TOP_TEN).size());
    }

    @Test
    void theBoardIsLimitedToTenCleanersOrderedByPoints() {
        for (int i = 1; i <= 12; i++) {
            save("Bulk Cleaner " + i, i, "Rajasthan", "Jaipur", "Jaipur", "249900");
        }

        List<User> board = userRepository.findTop10InDistrict(
                Role.ROLE_CLEANER, "Rajasthan", "Jaipur", TOP_TEN);

        assertEquals(10, board.size());
        assertEquals(12, board.get(0).getRewardPoints());
        assertEquals(3, board.get(9).getRewardPoints());
    }

    @Test
    void thePersonalRankCountsReproduceTheBoardPositionForTiedPoints() {
        // Board order: points descending, ties broken by id ascending. The
        // cleaner seeded last among three 90-point rows sits behind the
        // leader (100) AND behind the two equal-point rows saved earlier.
        save("Rank Leader", 100, "Bihar", "Gaya", "Gaya", "249680");
        save("Tied First", 90, "Punjab", "S.A.S Nagar", "Mohali", "248325");
        save("Tied Second", 90, "Punjab", "S.A.S Nagar", "Mohali", "248325");
        User me = save("Tied Last", 90, "Punjab", "S.A.S Nagar", "Mohali", "248325");

        long rankedAbove = userRepository.countByRoleAndRewardPointsGreaterThan(
                Role.ROLE_CLEANER, 90);
        long tiedRankedAhead = userRepository.countByRoleAndRewardPointsAndIdLessThan(
                Role.ROLE_CLEANER, 90, me.getId());

        // Board position = 1 + 2 + 1 = 3rd, the number the national board
        // displays for this row
        assertEquals(1, rankedAbove);
        assertEquals(2, tiedRankedAhead);

        // A zero-points cleaner counts everyone above them, ties included:
        // this test's four rows plus the seven positive-point rows the
        // shared fixture seeds
        assertEquals(11, userRepository.countByRoleAndRewardPointsGreaterThan(
                Role.ROLE_CLEANER, 0));
    }
}
