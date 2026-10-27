package com.cleanbharat.wastemanagement.service.location;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.exception.LocationDataUnavailableException;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService.ResolvedLocation;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the location directory.
 *
 * These run against the real generated resource, not a fixture: the whole point
 * of the directory is that it is the supplied LGD workbook, so a test against a
 * hand-made copy would prove nothing about what registration actually accepts.
 *
 * The codes used below are the workbook's own:
 *   Punjab 3, Amritsar district 27, Amritsar ULB 248273
 *   Punjab 3, S.A.S Nagar district 608, Mohali ULB 248325
 *   Bihar 10, Gaya district 196, Gaya ULB 249680
 */
class IndianLocationServiceTest {

    private static IndianLocationService service;
    private static JsonNode directory;

    @BeforeAll
    static void loadDirectory() {
        service = new IndianLocationService(new ObjectMapper());
        service.load();
        directory = service.directory();
    }

    // ------------------------------------------------------------------
    // Accepted combinations
    // ------------------------------------------------------------------

    @Test
    void aWorkbookTripleResolvesToTheCanonicalNames() {
        ResolvedLocation location = service.resolve("10", "196", "249680");

        // District and ULB codes were proof, not data: resolve() answers only
        // the two names that get stored on the user row
        assertEquals("Bihar", location.state());
        assertEquals("Gaya", location.city());
    }

    @Test
    void theStoredShapeMatchesWhatTheLeaderboardsQuery() {
        // LeaderboardServiceImpl normalises the typed scope with LocationUtil
        // and the repository matches exactly, so a stored value has to be in
        // that same shape. LocationUtil title-cases word by word - it lowercases
        // first - so the workbook's "S.A.S.Nagar - Mohali" is stored with its
        // acronym folded, exactly as a typed "S.A.S NAGAR" always was.
        ResolvedLocation location = service.resolve("3", "608", "248325");

        assertEquals("Punjab", location.state());
        assertEquals("S.a.s.nagar - Mohali", location.city());
    }

    @Test
    void surroundingWhitespaceOnTheCodesIsTolerated() {
        ResolvedLocation location = service.resolve(" 3 ", " 27 ", " 248273 ");

        assertEquals("Amritsar", location.city());
    }

    // ------------------------------------------------------------------
    // Rejected combinations
    // ------------------------------------------------------------------

    @Test
    void punjabPlusGayaIsRejected() {
        InvalidRegistrationException refused = assertThrows(
                InvalidRegistrationException.class,
                () -> service.resolve("3", "196", "249680"));

        // Names the state it really belongs to, so the mistake is fixable
        assertTrue(refused.getMessage().contains("Bihar"), refused.getMessage());
    }

    @Test
    void biharPlusAmritsarIsRejected() {
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "196", "248273"));
    }

    @Test
    void aDistrictFromAnotherStateIsRejected() {
        // Amritsar is a Punjab district; asking for it under Bihar fails at the
        // district level, before the ULB is even considered
        InvalidRegistrationException refused = assertThrows(
                InvalidRegistrationException.class,
                () -> service.resolve("10", "27", "248273"));

        assertTrue(refused.getMessage().contains("Punjab"), refused.getMessage());
    }

    @Test
    void aUlbFromAnotherDistrictOfTheSameStateIsRejected() {
        // Amritsar's corporation is not in the Gaya district of Bihar
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "196", "248273"));
    }

    @Test
    void aDistrictCodeSubmittedAsAUlbCodeIsRejected() {
        // "196" is a district code, not a ULB code
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "196", "196"));
    }

    @Test
    void aDistrictNameSubmittedAsAUlbCodeIsRejected() {
        // Names are never accepted where the API requires an LGD code
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "196", "Gaya"));
    }

    @Test
    void anUnknownStateIsRejected() {
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("999", "196", "249680"));
    }

    @Test
    void anUnknownDistrictIsRejected() {
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "999", "249680"));
    }

    @Test
    void anUnknownUlbIsRejected() {
        assertThrows(InvalidRegistrationException.class,
                () -> service.resolve("10", "196", "999999"));
    }

    @Test
    void blankCodesAreRejectedRatherThanDefaulted() {
        assertThrows(InvalidRegistrationException.class, () -> service.resolve(null, "196", "249680"));
        assertThrows(InvalidRegistrationException.class, () -> service.resolve("10", "", "249680"));
        assertThrows(InvalidRegistrationException.class, () -> service.resolve("10", "196", "   "));
    }

    // ------------------------------------------------------------------
    // Source-faithful behaviour
    // ------------------------------------------------------------------

    @Test
    void aUlbListedUnderSeveralDistrictsResolvesUnderEachOfThem() {
        // The workbook associates some ULB codes with more than one district.
        // Those associations are the source's answer and must survive: dropping
        // the extra listing would refuse a citizen who lives in the second one.
        Map<String, Map<String, String>> placesByCode = new LinkedHashMap<>();

        for (JsonNode state : directory.path("states")) {
            for (JsonNode district : state.path("districts")) {
                for (JsonNode ulb : district.path("ulbs")) {
                    placesByCode
                            .computeIfAbsent(ulb.path("code").asText(), code -> new LinkedHashMap<>())
                            .putIfAbsent(
                                    state.path("code").asText() + "/" + district.path("code").asText(),
                                    ulb.path("name").asText());
                }
            }
        }

        Map.Entry<String, Map<String, String>> repeated = placesByCode.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "the workbook is expected to list at least one ULB under several districts"));

        for (String parent : repeated.getValue().keySet()) {
            String[] codes = parent.split("/");
            ResolvedLocation location = service.resolve(codes[0], codes[1], repeated.getKey());

            // The city comes back in the stored shape, so compare against the
            // workbook's name run through the same normalisation
            assertEquals(
                    LocationUtil.normalizeLocation(repeated.getValue().get(parent)),
                    location.city());
        }
    }

    @Test
    void theDirectoryCarriesEveryWorkbookLevel() {
        // The generated counts, pinned so a truncated regeneration is caught
        assertEquals(36, directory.path("counts").path("states").asInt());
        assertEquals(752, directory.path("counts").path("districts").asInt());
        assertEquals(5059, directory.path("counts").path("ulbs").asInt());
        assertEquals(5094, directory.path("counts").path("districtUlbRows").asInt());
    }

    @Test
    void aStateWithoutUlbRowsIsListedButResolvesNothing() {
        // The supplied export is a ULB export: it carries no Lakshadweep rows,
        // which is not evidence that the Union Territory does not exist. The
        // state still appears in the selector, but with no districts and no
        // municipal areas - and any code beneath it is refused, never guessed.
        JsonNode lakshadweep = stateByCode("31");
        assertNotNull(lakshadweep, "Lakshadweep (state 31) missing from the directory");
        assertEquals("Lakshadweep", lakshadweep.path("name").asText());
        assertEquals(0, lakshadweep.path("districts").size());

        // Ladakh keeps its own code and its workbook rows: no supplement collision
        JsonNode ladakh = stateByCode("37");
        assertNotNull(ladakh, "Ladakh (state 37) missing from the directory");
        assertTrue(ladakh.path("districts").size() > 0);

        InvalidRegistrationException refused = assertThrows(
                InvalidRegistrationException.class,
                () -> service.resolve("31", "196", "249680"));

        assertTrue(refused.getMessage().contains("Lakshadweep"), refused.getMessage());
    }

    private static JsonNode stateByCode(String code) {
        for (JsonNode state : directory.path("states")) {
            if (code.equals(state.path("code").asText())) {
                return state;
            }
        }
        return null;
    }

    @Test
    void anUnreadableDirectoryFailsLoudlyRatherThanAcceptingAnything() {
        // What a missing or malformed resource does: every lookup is refused as
        // unavailable, never answered with a permissive default.
        IndianLocationService broken = new IndianLocationService(new ObjectMapper()) {
            @Override
            void load() {
                // Deliberately never loads
            }
        };

        assertThrows(LocationDataUnavailableException.class,
                () -> broken.resolve("10", "196", "249680"));
        assertThrows(LocationDataUnavailableException.class, broken::directory);
    }
}
