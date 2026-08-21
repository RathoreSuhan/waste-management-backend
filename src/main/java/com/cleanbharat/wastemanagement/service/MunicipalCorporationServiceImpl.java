package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.exception.EmailAlreadyExistsException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // Marks this class as a Spring Service Bean
@RequiredArgsConstructor // Constructor Injection
public class MunicipalCorporationServiceImpl implements MunicipalCorporationService {

    private final MunicipalCorporationRepository municipalCorporationRepository;
    private final UserRepository userRepository;   // citizen/cleaner accounts share the same email space
    private final PasswordEncoder passwordEncoder; // corporation passwords are stored hashed, never in plain text

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

        MunicipalCorporation municipalCorporation = MunicipalCorporation.builder()
                        .city(request.getCity())
                        .organizationName(request.getOrganizationName())
                        .phone(request.getPhone())
                        .email(request.getEmail())
                        // Admin never types a password: the city body starts on the shared default
                        .password(passwordEncoder.encode(DEFAULT_MUNICIPAL_PASSWORD))
                        .build();

        MunicipalCorporation savedMunicipalCorporation = municipalCorporationRepository.save(municipalCorporation);

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
    public MunicipalCorporationResponse updateMunicipalCorporation(
            Long id,
            MunicipalCorporationRequest request) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        // Contact details may be corrected freely, but the login id must stay unique
        assertEmailIsFree(request.getEmail(), municipalCorporation.getId());

        municipalCorporation.setCity(request.getCity());
        municipalCorporation.setOrganizationName(request.getOrganizationName());
        municipalCorporation.setPhone(request.getPhone());
        municipalCorporation.setEmail(request.getEmail());
        // Password is deliberately untouched here: editing a phone number must not
        // silently reset a corporation that has already chosen its own password.

        MunicipalCorporation updatedMunicipalCorporation = municipalCorporationRepository.save(municipalCorporation);

        return mapToResponse(updatedMunicipalCorporation);
    }

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

    // Entity -> Response DTO (password is intentionally never exposed)
    private MunicipalCorporationResponse mapToResponse(MunicipalCorporation municipalCorporation) {

        return MunicipalCorporationResponse.builder()
                .id(municipalCorporation.getId())
                .city(municipalCorporation.getCity())
                .organizationName(municipalCorporation.getOrganizationName())
                .phone(municipalCorporation.getPhone())
                .email(municipalCorporation.getEmail())
                .build();
    }
}