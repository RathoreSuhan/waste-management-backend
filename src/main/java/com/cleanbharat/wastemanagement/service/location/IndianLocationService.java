package com.cleanbharat.wastemanagement.service.location;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.exception.LocationDataUnavailableException;
import com.cleanbharat.wastemanagement.util.LocationUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ============================================================
 *  Location directory
 * ============================================================
 *
 *  The single place that decides whether a state, district and urban local
 *  body (ULB) combination is real.
 *
 *  WHERE IT COMES FROM
 *  -------------------
 *  locations/india-ulb-directory.json, generated from the LGD workbook in
 *  locations/source/ by scripts/build_location_directory.py. That file is the
 *  only source of locations in the application - no third-party call at
 *  registration time, no list maintained by hand. Regenerating it is how the
 *  directory is refreshed. See locations/README.md.
 *
 *  WHAT IS TRUSTED
 *  ---------------
 *  The caller sends three codes. Names and type labels are never accepted as
 *  proof of anything; they are only what this service looks up and returns.
 *  That is what makes a tampered request fail even when it carries a plausible
 *  "Punjab" and "Gaya".
 *
 *  A ULB the workbook lists under more than one district is valid under each
 *  of them, exactly as the source says - the lookup is keyed on the whole
 *  state/district/ULB triple, never on the ULB code alone.
 *
 *  WHEN THE FILE IS MISSING OR MALFORMED
 *  -------------------------------------
 *  The application still starts - the rest of the site does not depend on it -
 *  but every lookup answers 503. A broken dataset must never mean "no location
 *  rules today".
 * ============================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IndianLocationService {

    static final String DIRECTORY_PATH = "locations/india-ulb-directory.json";

    private final ObjectMapper objectMapper;

    /** The generated directory as read; null until it has loaded successfully. */
    private JsonNode document;

    /* Lookups built from it. Codes are the only keys - names are never matched. */
    private Map<String, String> stateNames = Map.of();       // state code -> name
    private Map<String, String> districtNames = Map.of();    // "state/district" -> name
    private Map<String, String> ulbNames = Map.of();         // "state/district/ulb" -> name

    /* Which states list a code, used only to word a refusal helpfully. */
    private Map<String, Set<String>> statesOfDistrict = Map.of();
    private Map<String, Set<String>> statesOfUlb = Map.of();

    /* ULB code -> every directory home it appears under, for describing an
       already-mapped row. Read-only display use; never a validation path. */
    private Map<String, List<UlbDirectoryEntry>> ulbsByCode = Map.of();

    /**
     * Loads (or reloads) the bundled directory. Public so the migration
     * runner's tests - which live in another package - can drive it without
     * a Spring context; safe to call repeatedly, it just re-reads and
     * re-indexes the resource.
     */
    @PostConstruct
    public void load() {
        try (InputStream in = new ClassPathResource(DIRECTORY_PATH).getInputStream()) {

            index(objectMapper.readTree(in));

            log.info("Location directory loaded: {} states, {} districts, {} district/ULB entries",
                    stateNames.size(), districtNames.size(), ulbNames.size());

        } catch (Exception ex) {
            // document stays null, so every lookup answers 503 instead of accepting anything
            log.error("Location directory at {} could not be loaded", DIRECTORY_PATH, ex);
        }
    }

    /**
     * Builds the lookups from a directory document.
     *
     * Nothing is published until the whole document has been read, so a file
     * that turns out to be malformed part-way through leaves the service
     * unavailable rather than half-populated.
     *
     * @throws IllegalStateException when the document is not a usable directory
     */
    void index(JsonNode root) {

        Map<String, String> states = new HashMap<>();
        Map<String, String> districts = new HashMap<>();
        Map<String, String> ulbs = new HashMap<>();
        Map<String, Set<String>> districtHomes = new HashMap<>();
        Map<String, Set<String>> ulbHomes = new HashMap<>();
        Map<String, List<UlbDirectoryEntry>> ulbsByCode = new HashMap<>();

        for (JsonNode state : root.path("states")) {
            String stateCode = required(state, "code");
            states.put(stateCode, required(state, "name"));

            for (JsonNode district : state.path("districts")) {
                String districtCode = required(district, "code");
                districts.put(stateCode + "/" + districtCode, required(district, "name"));
                districtHomes.computeIfAbsent(districtCode, code -> new LinkedHashSet<>()).add(stateCode);

                for (JsonNode ulb : district.path("ulbs")) {
                    String ulbCode = required(ulb, "code");
                    String ulbType = required(ulb, "type"); // the dropdown shows it, so a row without one is malformed
                    String ulbName = required(ulb, "name");
                    ulbs.put(stateCode + "/" + districtCode + "/" + ulbCode, ulbName);
                    ulbHomes.computeIfAbsent(ulbCode, code -> new LinkedHashSet<>()).add(stateCode);
                    ulbsByCode.computeIfAbsent(ulbCode, code -> new ArrayList<>())
                            .add(new UlbDirectoryEntry(
                                    states.get(stateCode),
                                    districts.get(stateCode + "/" + districtCode),
                                    ulbName,
                                    ulbType));
                }
            }
        }

        if (ulbs.isEmpty()) {
            throw new IllegalStateException("The location directory lists no urban local bodies");
        }

        this.stateNames = states;
        this.districtNames = districts;
        this.ulbNames = ulbs;
        this.statesOfDistrict = districtHomes;
        this.statesOfUlb = ulbHomes;
        this.ulbsByCode = ulbsByCode;
        this.document = root; // published last: it is what marks the service usable
    }

    /**
     * The directory exactly as generated, for the sign-up form.
     *
     * Returned as the parsed tree rather than re-mapped into DTOs: it is
     * read-only reference data, and handing back the document read at startup
     * keeps the endpoint free of per-request work.
     *
     * @throws LocationDataUnavailableException when the directory is unusable
     */
    public JsonNode directory() {
        return requireDirectory();
    }

    /**
     * Resolve the submitted codes to a location that is safe to store.
     *
     * @throws InvalidRegistrationException when any level is missing, unknown,
     *         or does not belong to its parent - answered as a 400
     * @throws LocationDataUnavailableException when the directory itself is
     *         unusable - answered as a 503, never as an acceptance
     */
    public ResolvedLocation resolve(String stateCode, String districtCode, String ulbCode) {

        requireDirectory();

        if (!StringUtils.hasText(stateCode) || !StringUtils.hasText(districtCode)
                || !StringUtils.hasText(ulbCode)) {
            throw new InvalidRegistrationException(
                    "Select your state, district and city or municipal area.");
        }

        String state = stateCode.trim();
        String district = districtCode.trim();
        String ulb = ulbCode.trim();

        String stateName = stateNames.get(state);

        if (stateName == null) {
            throw new InvalidRegistrationException(
                    "The selected state or Union Territory is not recognised. Please choose one from the list.");
        }

        String districtName = districtNames.get(state + "/" + district);

        if (districtName == null) {
            throw new InvalidRegistrationException(
                    "The selected district is not in " + stateName + "."
                            + listedElsewhere(statesOfDistrict.get(district), state)
                            + " Please choose a district from the list.");
        }

        String ulbName = ulbNames.get(state + "/" + district + "/" + ulb);

        if (ulbName == null) {
            String elsewhere = listedElsewhere(statesOfUlb.get(ulb), state);

            throw new InvalidRegistrationException(elsewhere.isEmpty()
                    ? "The selected city or municipal area is not listed in " + districtName
                            + " district, " + stateName + ". Please choose one from the list."
                    : "The selected city or municipal area is not in " + stateName + "." + elsewhere
                            + " Please select its state and district first.");
        }

        /*
          Stored in the shape LocationUtil gives, because the state and city
          leaderboards normalise what a visitor types the same way and then
          match it exactly. The name itself is the workbook's ULB name - never
          the type, never a shortened form. The district name and ULB code are
          carried alongside: the district is what cleaner eligibility and the
          district leaderboard group on, and the code is what routes a report
          to the one Municipal Corporation configured for that exact ULB.
        */
        return new ResolvedLocation(
                LocationUtil.normalizeLocation(stateName),
                LocationUtil.normalizeLocation(districtName),
                LocationUtil.normalizeLocation(ulbName),
                ulb);
    }

    private JsonNode requireDirectory() {
        if (document == null) {
            throw new LocationDataUnavailableException(
                    "Location data is temporarily unavailable, so registration cannot be completed right now. "
                            + "Please try again shortly.");
        }
        return document;
    }

    /**
     * Maps a stored state/city NAME pair back to its directory entry.
     *
     * This is the offline reverse of {@link #resolve}: the migration runner
     * (and only the migration runner) uses it to give rows that were stored
     * before the refactor their district and ULB code. It must never be used
     * to validate a client request - names are not proof, codes are, and
     * every live validation path stays on {@link #resolve}.
     *
     * The match is exact against the canonical (LocationUtil-normalised)
     * directory names within the given state, so a row whose stored text
     * differs by even one character stays unmapped and lands on the audit
     * worklist instead of being guessed at. A ULB the workbook lists under
     * several districts of the same state is reported ambiguous rather than
     * silently mapped to one of them.
     *
     * @return a {@link NameMatch} whose location is null when the pair cannot
     *         be mapped unambiguously; {@code reason} then says exactly why
     */
    public NameMatch matchByNames(String stateName, String cityName) {

        requireDirectory();

        if (!StringUtils.hasText(stateName) || !StringUtils.hasText(cityName)) {
            return new NameMatch(null, "missing state or city value");
        }

        String wantedState = LocationUtil.normalizeLocation(stateName);
        String wantedCity = LocationUtil.normalizeLocation(cityName);

        String stateCode = null;
        for (Map.Entry<String, String> entry : stateNames.entrySet()) {
            if (LocationUtil.normalizeLocation(entry.getValue()).equals(wantedState)) {
                stateCode = entry.getKey();
                break;
            }
        }

        if (stateCode == null) {
            return new NameMatch(null, "state '" + wantedState + "' is not in the directory");
        }

        /*
          A ULB code may appear under several districts; within one state that
          is an ambiguity the backfill must not resolve on its own.
        */
        Map<String, Set<String>> districtsOfUlb = new LinkedHashMap<>();
        String matchedUlbName = null;

        for (Map.Entry<String, String> entry : ulbNames.entrySet()) {

            if (!entry.getKey().startsWith(stateCode + "/")) {
                continue;
            }
            if (!LocationUtil.normalizeLocation(entry.getValue()).equals(wantedCity)) {
                continue;
            }

            String[] codes = entry.getKey().split("/");
            districtsOfUlb.computeIfAbsent(codes[2], code -> new LinkedHashSet<>()).add(codes[1]);
            matchedUlbName = entry.getValue();
        }

        String stateDisplay = stateNames.get(stateCode);

        if (matchedUlbName == null) {
            return new NameMatch(null, "no directory ULB named '" + wantedCity + "' in " + stateDisplay);
        }

        if (districtsOfUlb.size() > 1
                || districtsOfUlb.values().iterator().next().size() > 1) {
            return new NameMatch(null, "ULB '" + wantedCity + "' is listed under several districts of "
                    + stateDisplay);
        }

        String ulbCode = districtsOfUlb.keySet().iterator().next();
        String districtCode = districtsOfUlb.values().iterator().next().iterator().next();

        return new NameMatch(
                new ResolvedLocation(
                        LocationUtil.normalizeLocation(stateDisplay),
                        LocationUtil.normalizeLocation(districtNames.get(stateCode + "/" + districtCode)),
                        LocationUtil.normalizeLocation(matchedUlbName),
                        ulbCode),
                null);
    }

    /**
     * One reverse lookup by stored names: either an unambiguous directory
     * location, or a reason for the audit worklist (never both).
     */
    public record NameMatch(ResolvedLocation location, String reason) {

        /** True when the pair was mapped to exactly one directory entry. */
        public boolean mapped() {
            return location != null;
        }
    }

    /**
     * Describes the official directory home of an already-mapped ULB code,
     * for read-only display on a Municipal Corporation profile.
     *
     * This is NOT a validation path: the caller's ulbCode was accepted by
     * {@link #resolve} when an administrator mapped the row, and nothing
     * here re-decides what is real. A code the workbook lists under more
     * than one district is disambiguated against the row's stored canonical
     * ULB name; when even that leaves several homes (or the stored name has
     * drifted) the answer is null rather than a guess - the profile then
     * shows an unavailable mapping instead of a possibly wrong district.
     *
     * @return the single described home, or null when unknown/ambiguous
     * @throws LocationDataUnavailableException when the directory itself is
     *         unusable - a caller enriching a display decides how to degrade
     */
    public UlbDirectoryEntry describeByUlbCode(String ulbCode, String storedCityName) {

        requireDirectory();

        if (!StringUtils.hasText(ulbCode)) {
            return null;
        }

        List<UlbDirectoryEntry> homes = ulbsByCode.get(ulbCode.trim());

        if (homes == null || homes.isEmpty()) {
            return null;
        }

        if (homes.size() == 1) {
            return homes.get(0);
        }

        if (!StringUtils.hasText(storedCityName)) {
            return null;
        }

        String wanted = LocationUtil.normalizeLocation(storedCityName);

        List<UlbDirectoryEntry> byName = homes.stream()
                .filter(home -> LocationUtil.normalizeLocation(home.ulb()).equals(wanted))
                .toList();

        return byName.size() == 1 ? byName.get(0) : null;
    }

    /**
     * The directory's description of one ULB home: the canonical state,
     * district and ULB names plus the body type, all as the workbook spells
     * them (not LocationUtil-normalised - display text, not match keys).
     */
    public record UlbDirectoryEntry(String state, String district, String ulb, String type) {
    }

    /**
     * " It is listed under Bihar." when a code belongs only to other states, so
     * an honest mix-up gets a message that says how to fix it. Empty when the
     * code is unknown everywhere, or is also listed under the submitted state.
     */
    private String listedElsewhere(Set<String> homes, String submittedState) {
        if (homes == null || homes.contains(submittedState)) {
            return "";
        }
        return " It is listed under "
                + homes.stream().map(stateNames::get).collect(Collectors.joining(" and ")) + ".";
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("A location directory entry has no " + field);
        }
        return value;
    }

    /**
     * A validated selection, ready to store on the location columns.
     *
     * @param state    canonical state name, in LocationUtil shape
     * @param district canonical district name, in LocationUtil shape
     * @param city     the workbook's ULB name, in LocationUtil shape
     * @param ulbCode  the directory's ULB code, for authoritative routing
     */
    public record ResolvedLocation(String state, String district, String city, String ulbCode) {
    }
}
