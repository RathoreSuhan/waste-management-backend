package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.AuthResponse;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.EmailAlreadyExistsException;
import com.cleanbharat.wastemanagement.exception.InvalidCredentialsException;
import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedRegistrationException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for sign-up and sign-in.
 * Focus: a city's Municipal Corporation is an admin-created identity, so nobody can sign up
 * for municipal powers, and the corporation's registered email signs in through its own row -
 * while Citizen and Cleaner accounts keep working exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CORPORATION_EMAIL = "mcmovali@gmail.com"; // registered by the admin for Mohali
    private static final String CORPORATION_PASSWORD_HASH = "encoded-municipal-password"; // BCrypt hash stands in as a stub
    private static final String CITIZEN_EMAIL = "citizen.one@example.com";
    private static final String ISSUED_TOKEN = "issued.jwt.token";

    @Mock private UserRepository userRepository;
    @Mock private MunicipalCorporationRepository municipalCorporationRepository; // corporations are their own login table
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    // ---------------------------------------------------------------------
    // REGISTRATION
    // ---------------------------------------------------------------------

    @Test
    void municipalOfficerRoleCannotBeSelfRegistered() {
        RegisterRequest request = registerRequest(Role.ROLE_MUNICIPAL_OFFICER, null);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);

        assertThrows(UnauthorizedRegistrationException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class)); // no shortcut into the Municipal Dashboard
    }

    @Test
    void adminRoleCannotBeSelfRegistered() {
        RegisterRequest request = registerRequest(Role.ROLE_ADMIN, null);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);

        assertThrows(UnauthorizedRegistrationException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void corporationEmailCannotBeReusedForANormalAccount() {
        RegisterRequest request = registerRequest(Role.ROLE_CITIZEN, null);
        request.setEmail(CORPORATION_EMAIL); // someone tries to claim the city's official inbox

        when(userRepository.existsByEmail(CORPORATION_EMAIL)).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(CORPORATION_EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void cleanerMustDeclareItsCleanerType() {
        RegisterRequest request = registerRequest(Role.ROLE_CLEANER, null); // cleaner type left blank

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);

        assertThrows(InvalidRegistrationException.class, () -> authService.register(request));
    }

    @Test
    void cleanerRegistrationWithMunicipalCrewTypeStillCreatesAPlainCleaner() {
        // Picking the MUNICIPAL crew type only describes the crew, it grants no approval powers
        RegisterRequest request = registerRequest(Role.ROLE_CLEANER, CleanerType.MUNICIPAL);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-user-password");

        authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.ROLE_CLEANER, saved.getValue().getRole());          // still just a cleaner
        assertEquals(CleanerType.MUNICIPAL, saved.getValue().getCleanerType()); // crew type only
    }

    @Test
    void citizenRegistrationStillWorks() {
        RegisterRequest request = registerRequest(Role.ROLE_CITIZEN, null);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(municipalCorporationRepository.existsByEmailIgnoreCase(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-user-password");

        authService.register(request);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.ROLE_CITIZEN, saved.getValue().getRole());
        assertEquals(0, saved.getValue().getRewardPoints()); // fresh account starts at zero
    }

    // ---------------------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------------------

    @Test
    void corporationSignsInWithTheDefaultPasswordAndGetsTheMunicipalRole() {
        MunicipalCorporation corporation = corporation();
        // The password the admin hands over with a newly registered corporation
        String issuedPassword = MunicipalCorporationServiceImpl.DEFAULT_MUNICIPAL_PASSWORD;

        when(municipalCorporationRepository.findByEmailIgnoreCase(CORPORATION_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(passwordEncoder.matches(issuedPassword, CORPORATION_PASSWORD_HASH)).thenReturn(true);
        when(jwtService.generateToken(CORPORATION_EMAIL)).thenReturn(ISSUED_TOKEN);

        AuthResponse response = authService.login(loginRequest(CORPORATION_EMAIL, issuedPassword));

        assertEquals(ISSUED_TOKEN, response.getToken());
        assertEquals(CORPORATION_EMAIL, response.getEmail());
        // The role is implied by being a registered corporation, never self-declared
        assertEquals(Role.ROLE_MUNICIPAL_OFFICER, response.getRole());
        verifyNoInteractions(userRepository); // corporations never touch the users table
    }

    @Test
    void corporationSignInIsRefusedWhenThePasswordIsWrong() {
        MunicipalCorporation corporation = corporation();

        when(municipalCorporationRepository.findByEmailIgnoreCase(CORPORATION_EMAIL))
                .thenReturn(Optional.of(corporation));
        when(passwordEncoder.matches("guessed-password", CORPORATION_PASSWORD_HASH)).thenReturn(false);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(loginRequest(CORPORATION_EMAIL, "guessed-password")));

        verifyNoInteractions(jwtService); // no token leaves the building
    }

    @Test
    void citizenSignInIsUnaffectedByTheMunicipalLookup() {
        User citizen = User.builder()
                .id(3L)
                .name("Citizen One")
                .email(CITIZEN_EMAIL)
                .password("encoded-user-password")
                .role(Role.ROLE_CITIZEN)
                .state("Punjab")
                .city("Mohali")
                .build();

        // No corporation owns this email, so the normal user table answers
        when(municipalCorporationRepository.findByEmailIgnoreCase(CITIZEN_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByEmail(CITIZEN_EMAIL)).thenReturn(Optional.of(citizen));
        when(passwordEncoder.matches("citizen-password", "encoded-user-password")).thenReturn(true);
        when(jwtService.generateToken(CITIZEN_EMAIL)).thenReturn(ISSUED_TOKEN);

        AuthResponse response = authService.login(loginRequest(CITIZEN_EMAIL, "citizen-password"));

        assertEquals(ISSUED_TOKEN, response.getToken());
        assertEquals(Role.ROLE_CITIZEN, response.getRole());
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    /** The Mohali row an admin created under Municipal Bodies, which is the login identity itself. */
    private MunicipalCorporation corporation() {
        return MunicipalCorporation.builder()
                .id(10L)
                .organizationName("Municipal Corporation SAS Nagar Mohali")
                .city("Mohali")
                .phone("0172-5044910")
                .email(CORPORATION_EMAIL)
                .password(CORPORATION_PASSWORD_HASH)
                .build();
    }

    private RegisterRequest registerRequest(Role role, CleanerType cleanerType) {
        return RegisterRequest.builder()
                .name("Applicant")
                .email("applicant@example.com")
                .password("applicant-password")
                .role(role)
                .cleanerType(cleanerType)
                .state("Punjab")
                .city("Mohali")
                .build();
    }

    private LoginRequest loginRequest(String email, String password) {
        return LoginRequest.builder()
                .email(email)
                .password(password)
                .build();
    }
}