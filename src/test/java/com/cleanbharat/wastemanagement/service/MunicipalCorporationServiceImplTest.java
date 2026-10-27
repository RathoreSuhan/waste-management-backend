package com.cleanbharat.wastemanagement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
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

    @InjectMocks
    private MunicipalCorporationServiceImpl municipalCorporationService;

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
    void updateOwnCorporationUpdatesOnlyCorporationResolvedFromAuthenticatedEmail() {
        MunicipalCorporation corporation = corporation();
        MunicipalCorporationRequest request = request(
                "SAS Nagar",
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
        assertEquals("SAS Nagar", response.getCity());
        assertEquals("Municipal Corporation SAS Nagar", response.getOrganizationName());
        assertEquals("+91 9876543210", response.getPhone());
        assertEquals("office@sasnagar.gov.in", response.getEmail());
        assertEquals("encoded-password", corporation.getPassword());
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
}