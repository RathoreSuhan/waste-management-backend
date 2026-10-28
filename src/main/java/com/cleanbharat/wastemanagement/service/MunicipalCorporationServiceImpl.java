package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.exception.EmailAlreadyExistsException;
import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService.ResolvedLocation;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // Marks this class as a Spring Service Bean
@RequiredArgsConstructor // Constructor Injection
public class MunicipalCorporationServiceImpl implements MunicipalCorporationService {

    private final MunicipalCorporationRepository municipalCorporationRepository;
    private final UserRepository userRepository;   // citizen/cleaner accounts share the same email space
    private final PasswordEncoder passwordEncoder; // corporation passwords are stored hashed, never in plain text
    private final IndianLocationService indianLocationService; // validates the directory triple on the cascade path

    /**
     * Password every newly registered corporation starts with.
     * Handed over to the municipal body offline; they are expected to change it
     * from the Change Password screen after their first login.
     */
    public static final String DEFAULT_MUNICIPAL_PASSWORD = "mc123456";

    @Override
    public MunicipalCorporationResponse createMunicipalCorporation(MunicipalCorporationRequest request) {

        // The email is this corporation's login id, so it must be unique everywhere
        assertEmailIsFree(request.getEmail(), null);

        /*
          Two accepted shapes. The directory cascade (preferred) resolves the
          three LGD codes into the canonical ULB name + code and guarantees one
          corporation per ULB; the legacy shape stores the free-text city
          verbatim, exactly as the old admin form always did.
        */
        ResolvedLocation location = resolveLocationOrCity(request);

        MunicipalCorporation.MunicipalCorporationBuilder builder = MunicipalCorporation.builder()
                .organizationName(request.getOrganizationName())
                .phone(request.getPhone())
                .email(request.getEmail())
                // Admin never types a password: the city body starts on the shared default
                .password(passwordEncoder.encode(DEFAULT_MUNICIPAL_PASSWORD));

        if (location != null) {

            if (municipalCorporationRepository.existsByUlbCode(location.ulbCode())) {
                throw new InvalidRegistrationException(
                        "A Municipal Corporation is already registered for \""
                                + location.city() + "\" (ULB " + location.ulbCode() + ").");
            }

            builder.city(location.city()).ulbCode(location.ulbCode());
        } else {
            builder.city(request.getCity()); // legacy verbatim, no code
        }

        MunicipalCorporation savedMunicipalCorporation =
                municipalCorporationRepository.save(builder.build());

        return mapToResponse(savedMunicipalCorporation);
    }

    @Override
    public List<MunicipalCorporationResponse> getAllMunicipalCorporations() {

        return municipalCorporationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public MunicipalCorporationResponse getByCity(String city) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findByCityIgnoreCase(city)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with city: " + city));

        return mapToResponse(municipalCorporation);
    }

    @Override
    public MunicipalCorporationResponse getById(Long id) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        return mapToResponse(municipalCorporation);
    }

    @Override
    public MunicipalCorporationResponse getOwnCorporation(String email) {
        return mapToResponse(findCorporationByEmail(email));
    }

    /*
     * The cached municipal overview carries this corporation's own
     * organizationName and city in its header, so an edit here makes the
     * entry wrong even though not one count changed.
     *
     * allEntries = true is required rather than merely tidy: line 98 can
     * change the email, and the email IS the cache key. Evicting the new
     * key would leave the old one behind holding the old name.
     */
    @CacheEvict(value = "municipal_dashboard_stats", allEntries = true)
    @Override
    public MunicipalCorporationResponse updateMunicipalCorporation(
            Long id,
            MunicipalCorporationRequest request) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        // Contact details may be corrected freely, but the login id must stay unique
        assertEmailIsFree(request.getEmail(), municipalCorporation.getId());

        applyUpdate(municipalCorporation, request, municipalCorporation.getId());

        MunicipalCorporation updatedMunicipalCorporation = municipalCorporationRepository.save(municipalCorporation);

        return mapToResponse(updatedMunicipalCorporation);
    }

    /**
     * Municipal officers never supply an id: the authenticated email resolves
     * their one corporation, preventing a city from editing another city's record.
     */
    @CacheEvict(value = "municipal_dashboard_stats", allEntries = true)
    @Override
    public MunicipalCorporationResponse updateOwnCorporation(
            String email,
            MunicipalCorporationRequest request) {

        MunicipalCorporation municipalCorporation = findCorporationByEmail(email);
        assertEmailIsFree(request.getEmail(), municipalCorporation.getId());

        applyUpdate(municipalCorporation, request, municipalCorporation.getId());

        return mapToResponse(municipalCorporationRepository.save(municipalCorporation));
    }

    /*
     * One update rule for both entry points (plan rule v4-1):
     *
     * When the request carries the directory codes, it is a ULB mapping: the
     * row gains its ulb_code (uniqueness-guarded) while the stored city is
     * left EXACTLY as it is - legacy NULL-code reports still route through
     * that value, so "Mohali" must never become "S.a.s.nagar - Mohali" as a
     * side effect of a mapping. When no codes are sent, the legacy free-text
     * shape applies and city is updated from the request, exactly as the old
     * form (which round-trips the stored value) always behaved.
     */
    private void applyUpdate(MunicipalCorporation municipalCorporation,
                             MunicipalCorporationRequest request,
                             Long selfId) {

        ResolvedLocation location = resolveLocationOrCity(request);

        if (location != null) {

            if (municipalCorporationRepository.existsByUlbCodeAndIdNot(location.ulbCode(), selfId)) {
                throw new InvalidRegistrationException(
                        "A Municipal Corporation is already registered for \""
                                + location.city() + "\" (ULB " + location.ulbCode() + ").");
            }

            municipalCorporation.setUlbCode(location.ulbCode());
            // city deliberately untouched: see v4-1 above

        } else {
            municipalCorporation.setCity(request.getCity()); // legacy verbatim
        }

        municipalCorporation.setOrganizationName(request.getOrganizationName());
        municipalCorporation.setPhone(request.getPhone());
        municipalCorporation.setEmail(request.getEmail());
        // Password is deliberately untouched here: editing a phone number must not
        // silently reset a corporation that has already chosen its own password.
    }

    /**
     * Validates the request's location shape: either the three LGD codes
     * together (resolved against the directory) or a legacy free-text city.
     *
     * @return the resolved location on the cascade path, or null on the
     *         legacy path
     */
    private ResolvedLocation resolveLocationOrCity(MunicipalCorporationRequest request) {

        boolean hasCodes = hasText(request.getStateCode())
                || hasText(request.getDistrictCode())
                || hasText(request.getUlbCode());

        if (!hasCodes) {

            if (!hasText(request.getCity())) {
                throw new InvalidRegistrationException(
                        "Select the corporation's state, district and city/municipal area "
                                + "(or send its legacy city name).");
            }

            return null;
        }

        if (!hasText(request.getStateCode()) || !hasText(request.getDistrictCode())
                || !hasText(request.getUlbCode())) {
            throw new InvalidRegistrationException(
                    "State, district and city/municipal-area codes must be sent together.");
        }

        return indianLocationService.resolve(
                request.getStateCode(),
                request.getDistrictCode(),
                request.getUlbCode());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    // Removing a city body retires its cached overview along with it
    @CacheEvict(value = "municipal_dashboard_stats", allEntries = true)
    @Override
    public void deleteMunicipalCorporation(Long id) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        municipalCorporationRepository.delete(municipalCorporation);
    }

    /**
     * An email may identify exactly one login across the whole platform.
     *
     * @param selfId id of the row being updated, so a corporation keeping its own
     *               email is not reported as a clash (null when creating).
     */
    private void assertEmailIsFree(String email, Long selfId) {

        // Clash with another corporation
        municipalCorporationRepository.findByEmailIgnoreCase(email)
                .filter(existing -> selfId == null || !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new EmailAlreadyExistsException(
                            "Another Municipal Corporation is already registered with email: " + email);
                });

        // Clash with a citizen / cleaner / admin account
        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyExistsException(
                    "A user account already exists with email: " + email);
        }
    }

    private MunicipalCorporation findCorporationByEmail(String email) {
        return municipalCorporationRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Municipal Corporation not found"));
    }

    // Entity -> Response DTO (password is intentionally never exposed)
    private MunicipalCorporationResponse mapToResponse(MunicipalCorporation municipalCorporation) {

        return MunicipalCorporationResponse.builder()
                .id(municipalCorporation.getId())
                .city(municipalCorporation.getCity())
                .ulbCode(municipalCorporation.getUlbCode())
                .organizationName(municipalCorporation.getOrganizationName())
                .phone(municipalCorporation.getPhone())
                .email(municipalCorporation.getEmail())
                .build();
    }
}