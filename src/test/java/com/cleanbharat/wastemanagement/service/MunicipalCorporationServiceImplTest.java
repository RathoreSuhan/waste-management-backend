package com.cleanbharat.wastemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalOfficerProfileRequest;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class MunicipalCorporationServiceImplTest {

    private static final String OFFICER_EMAIL = "mc@mohali.gov.in";

    @Mock
    private MunicipalCorporationRepository municipalCorporationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IndianLocationService indianLocationService;

    @InjectMocks
    private MunicipalCorporationServiceImpl municipalCorporationService;

    @Test
    void getByUlbCodeFindsTheCorporationWhenCityStringsDiffer() {
        MunicipalCorporation corporation = corporation();
        corporation.setUlbCode("248325");
        // The report stores the canonical directory name while this legacy
        // corporation deliberately keeps "Mohali". ULB code is the whole
        // location-V2 match, so the city mismatch is irrelevant.
        when(municipalCorporationRepository.findByUlbCode("248325"))
                .thenReturn(Optional.of(corporation));

        MunicipalCorporationResponse response = municipalCorporationService
                .getByUlbCode("248325");

        assertEquals("Mohali", response.getCity());
        assertEquals("248325", response.getUlbCode());
        assertEquals("Municipal Corporation Mohali", response.getOrganizationName());
        assertEquals("9876543210", response.getPhone());
        assertEquals(OFFICER_EMAIL, response.getEmail());
        verify(municipalCorporationRepository).findByUlbCode("248325");
    }

    @Test
    void getByUlbCodeRejectsAnUnconfiguredUlbWithoutFallingBackToCity() {
        when(municipalCorporationRepository.findByUlbCode("999999"))
                .thenReturn(Optional.empty());

        ResourceNotFoundException refused = assertThrows(
                ResourceNotFoundException.class,
                () -> municipalCorporationService.getByUlbCode("999999"));

        assertEquals(
                "Municipal Corporation not found with ULB code: 999999",
                refused.getMessage());
        verify(municipalCorporationRepository).findByUlbCode("999999");
        verify(municipalCorporationRepository, org.mockito.Mockito.never())
                .findByCityIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void getByCityPreservesTheLegacyLookupBehavior() {
        MunicipalCorporation corporation = corporation();
        when(municipalCorporationRepository.findByCityIgnoreCase("mohali"))
                .thenReturn(Optional.of(corporation));

        MunicipalCorporationResponse response = municipalCorporationService.getByCity("mohali");

        assertEquals(corporation.getId(), response.getId());
        assertEquals("Mohali", response.getCity());
        verify(municipalCorporationRepository).findByCityIgnoreCase("mohali");
    }

    @Test
    void getByCityStillReturnsNotFoundForAnUnknownLegacyCity() {
        when(municipalCorporationRepository.findByCityIgnoreCase("Unknown City"))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> municipalCorporationService.getByCity("Unknown City"));
    }

    @Test
    void getByUlbCodeKeepsAContactWhenOnlyOneOptionalFieldIsPopulated() {
        MunicipalCorporation phoneOnly = corporation();
        phoneOnly.setUlbCode("248325");
        phoneOnly.setEmail(null);
        when(municipalCorporationRepository.findByUlbCode("248325"))
                .thenReturn(Optional.of(phoneOnly));

        MunicipalCorporationResponse response = municipalCorporationService
                .getByUlbCode("248325");

        assertEquals("9876543210", response.getPhone());
        assertEquals(null, response.getEmail());
        assertEquals("Municipal Corporation Mohali", response.getOrganizationName());
    }

    @Test
    void getOwnCorporationUsesAuthenticatedEmail() {
        MunicipalCorporation corporation = corporation();
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));

        MunicipalCorporationResponse response = municipalCorporationService
                .getOwnCorporation(OFFICER_EMAIL);

        assertEquals(corporation.getId(), response.getId());
        assertEquals("Mohali", response.getCity());
        assertEquals(OFFICER_EMAIL, response.getEmail());
        verify(municipalCorporationRepository).findByEmailIgnoreCase(OFFICER_EMAIL);
    }

    @Test
    void updateOwnCorporationUpdatesOnlyTheContactDetails() {
        MunicipalCorporation corporation = corporation();
        MunicipalOfficerProfileRequest request = officerRequest(
                "Municipal Corporation SAS Nagar",
                "+91 9876543210",
                "office@sasnagar.gov.in"
        );
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(municipalCorporationRepository.findByEmailIgnoreCase(request.getEmail()))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.save(corporation)).thenReturn(corporation);

        MunicipalCorporationResponse response = municipalCorporationService
                .updateOwnCorporation(OFFICER_EMAIL, request);

        ArgumentCaptor<MunicipalCorporation> savedCorporation =
                ArgumentCaptor.forClass(MunicipalCorporation.class);
        verify(municipalCorporationRepository).save(savedCorporation.capture());
        assertEquals(corporation, savedCorporation.getValue());
        assertEquals("Municipal Corporation SAS Nagar", response.getOrganizationName());
        assertEquals("+91 9876543210", response.getPhone());
        assertEquals("office@sasnagar.gov.in", response.getEmail());
        assertEquals("encoded-password", corporation.getPassword());
    }

    @Test
    void updateOwnCorporationNeverTouchesTheStoredCityOrTheAssignedUlbMapping() {
        MunicipalCorporation corporation = corporation();
        corporation.setCity("Mohali"); // preserved legacy routing value
        corporation.setUlbCode("248325"); // administrator-assigned mapping
        MunicipalOfficerProfileRequest request = officerRequest(
                "Renamed Corporation",
                "9876500000",
                OFFICER_EMAIL
        );
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(userRepository.existsByEmail(OFFICER_EMAIL)).thenReturn(false);
        when(municipalCorporationRepository.save(corporation)).thenReturn(corporation);

        municipalCorporationService.updateOwnCorporation(OFFICER_EMAIL, request);

        assertEquals("Mohali", corporation.getCity());
        assertEquals("248325", corporation.getUlbCode());
    }

    @Test
    void getOwnCorporationExposesTheOfficialMappedJurisdiction() {
        MunicipalCorporation corporation = corporation();
        corporation.setUlbCode("248325");
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(indianLocationService.describeByUlbCode("248325", "Mohali"))
                .thenReturn(new IndianLocationService.UlbDirectoryEntry(
                        "Punjab", "S.A.S Nagar", "S.A.S.Nagar - Mohali", "Municipal Corporation"));

        MunicipalCorporationResponse response = municipalCorporationService
                .getOwnCorporation(OFFICER_EMAIL);

        MunicipalCorporationResponse.MappedLocation mapped = response.getMappedLocation();
        assertNotNull(mapped);
        assertEquals("Punjab", mapped.getState());
        assertEquals("S.A.S Nagar", mapped.getDistrict());
        assertEquals("S.A.S.Nagar - Mohali", mapped.getUlbName());
        assertEquals("Municipal Corporation", mapped.getUlbType());
        // The legacy city is still its own, separate value
        assertEquals("Mohali", response.getCity());
    }

    @Test
    void anUnmappedRowCarriesNoMappedLocation() {
        MunicipalCorporation corporation = corporation(); // no ulbCode
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));

        MunicipalCorporationResponse response = municipalCorporationService
                .getOwnCorporation(OFFICER_EMAIL);

        assertEquals(null, response.getMappedLocation());
        verify(indianLocationService, org.mockito.Mockito.never())
                .describeByUlbCode(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void anUndescribableUlbCodeAnswersAnUnavailableMappingRatherThanAGuess() {
        MunicipalCorporation corporation = corporation();
        corporation.setUlbCode("248325");
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(indianLocationService.describeByUlbCode("248325", "Mohali")).thenReturn(null);

        MunicipalCorporationResponse response = municipalCorporationService
                .getOwnCorporation(OFFICER_EMAIL);

        assertEquals(null, response.getMappedLocation());
    }

    @Test
    void getOwnCorporationRejectsAnEmailWithoutMunicipalCorporation() {
        when(municipalCorporationRepository.findByEmailIgnoreCase(OFFICER_EMAIL))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> municipalCorporationService.getOwnCorporation(OFFICER_EMAIL)
        );
    }

    private MunicipalCorporation corporation() {
        return MunicipalCorporation.builder()
                .id(17L)
                .city("Mohali")
                .organizationName("Municipal Corporation Mohali")
                .phone("9876543210")
                .email(OFFICER_EMAIL)
                .password("encoded-password")
                .build();
    }

    private MunicipalCorporationRequest request(
            String city,
            String organizationName,
            String phone,
            String email) {

        MunicipalCorporationRequest request = new MunicipalCorporationRequest();
        request.setCity(city);
        request.setOrganizationName(organizationName);
        request.setPhone(phone);
        request.setEmail(email);
        return request;
    }

    private MunicipalOfficerProfileRequest officerRequest(
            String organizationName,
            String phone,
            String email) {

        MunicipalOfficerProfileRequest request = new MunicipalOfficerProfileRequest();
        request.setOrganizationName(organizationName);
        request.setPhone(phone);
        request.setEmail(email);
        return request;
    }
}