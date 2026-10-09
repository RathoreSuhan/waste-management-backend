package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.AuthResponse;
import com.cleanbharat.wastemanagement.dto.GoogleAuthRequest;
import com.cleanbharat.wastemanagement.dto.GoogleAuthResponse;
import com.cleanbharat.wastemanagement.dto.GoogleRegisterRequest;
import com.cleanbharat.wastemanagement.dto.LoginRequest;
import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterResponse;
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
import com.cleanbharat.wastemanagement.service.GoogleIdentityService.GoogleIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for sign-up and sign-in.
 *
 * Three rules are worth pinning here.
 *
 * An account is created only from a Google identity, and the details that
 * matter - the address and the account id - are taken from the verified token
 * rather than from the request, so a caller cannot register an address they do
 * not own. The role still cannot be chosen freely: Municipal and Admin powers
 * are not something anybody signs up for, which was true of the old sign-up
 * form and has to stay true of this one.
 *
 * One Google account means one Clean Bharat account. A repeat sign-in must find
 * the linked row rather than create a second one, and a Google account whose
 * verified address already belongs to an older password account must be linked
 * to it - otherwise one person's reports, points and history split in two.
 *
 * And a city's Municipal Corporation is an admin-created identity that signs in
 * through its own row, so it must keep working exactly as before.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CORPORATION_EMAIL = "mcmovali@gmail.com"; // registered by the admin for Mohali
    private static final String CORPORATION_PASSWORD_HASH = "encoded-municipal-password"; // BCrypt hash stands in as a stub
    private static final String CITIZEN_EMAIL = "citizen.one@example.com";
    private static final String ISSUED_TOKEN = "issued.jwt.token";

    private static final String GOOGLE_CREDENTIAL = "google.id.token";
    private static final String GOOGLE_SUBJECT = "117554201923456789012"; // Google's 'sub'
    private static final String GOOGLE_EMAIL = "applicant@example.com";
    private static final String GOOGLE_NAME = "Applicant";

    /** Typed into the sign-up form, proved by an emailed code rather than Google. */
    private static final String TYPED_EMAIL = "applicant@yahoo.com";

    @Mock private UserRepository userRepository;
    @Mock private MunicipalCorporationRepository municipalCorporationRepository; // corporations are their own login table
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private GoogleIdentityService googleIdentityService; // verification itself is tested separately
    @Mock private EmailVerificationService emailVerificationService; // likewise

    @InjectMocks private AuthService authService;

    @BeforeEach
    void stubSaveReturnsManagedEntity() {
        // JpaRepository.save never returns null in production; Mockito does by default.
        // Without this, every test creating an account NPEs on the save result.
        // Lenient: many tests refuse before reaching save, so the stub is unused there.
        org.mockito.Mockito.lenient()
                .when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ---------------------------------------------------------------------
    // SIGN-UP WITH AN EMAILED CODE
    // ---------------------------------------------------------------------

    @Test
    void theFirstCallSendsACodeAndCreatesNothing() {
        noExistingAccount(TYPED_EMAIL);

        RegisterResponse response = authService.register(registerRequest(Role.ROLE_CITIZEN, null, null));

        assertTrue(response.isVerificationRequired());
        assertNull(response.getToken(), "nothing is authorised until the address is proved");
        assertEquals(TYPED_EMAIL, response.getEmail());

        verify(emailVerificationService).sendCode(TYPED_EMAIL);
        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService);
    }

    @Test
    void theCodeIsCheckedBeforeAnAccountIsWritten() {
        noExistingAccount(TYPED_EMAIL);

        when(passwordEncoder.encode("applicant-password")).thenReturn("encoded-user-password");
        when(jwtService.generateToken(TYPED_EMAIL)).thenReturn(ISSUED_TOKEN);

        RegisterResponse response = authService.register(
                registerRequest(Role.ROLE_CITIZEN, null, "123456"));

        // The order matters: a refused code must stop short of the insert
        InOrder order = inOrder(emailVerificationService, userRepository);
        order.verify(emailVerificationService).verifyCode(TYPED_EMAIL, "123456");
        order.verify(userRepository).save(any(User.class));

        assertFalse(response.isVerificationRequired());
        assertEquals(ISSUED_TOKEN, response.getToken());
        assertEquals(Role.ROLE_CITIZEN, response.getRole());
    }

    @Test
    void aWrongCodeCreatesNothing() {
        noExistingAccount(TYPED_EMAIL);

        doThrow(new InvalidRegistrationException("wrong code"))
                .when(emailVerificationService).verifyCode(TYPED_EMAIL, "000000");

        assertThrows(InvalidRegistrationException.class,
                () -> authService.register(registerRequest(Role.ROLE_CITIZEN, null, "000000")));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService);
    }

    @Test
    void aPasswordSignUpStoresTheChosenPasswordAndNoGoogleLink() {
        noExistingAccount(TYPED_EMAIL);

        when(passwordEncoder.encode("applicant-password")).thenReturn("encoded-user-password");
        when(jwtService.generateToken(TYPED_EMAIL)).thenReturn(ISSUED_TOKEN);

        authService.register(registerRequest(Role.ROLE_CITIZEN, null, "123456"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertEquals("encoded-user-password", saved.getValue().getPassword());
        assertNull(saved.getValue().getGoogleSubject(), "linked only if they later sign in with Google");
        assertEquals("Punjab", saved.getValue().getState()); // normalised by LocationUtil
        assertEquals(0, saved.getValue().getRewardPoints());
    }

    @Test
    void noCodeIsSentToAnAddressThatAlreadyHasAnAccount() {
        when(municipalCorporationRepository.existsByEmailIgnoreCase(TYPED_EMAIL)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(TYPED_EMAIL))
                .thenReturn(Optional.of(citizen(TYPED_EMAIL)));

        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.register(registerRequest(Role.ROLE_CITIZEN, null, null)));

        // Validating before sending is what keeps a doomed request from costing mail
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void passwordSignUpCannotCreateAMunicipalOfficer() {
        assertThrows(UnauthorizedRegistrationException.class,
                () -> authService.register(registerRequest(Role.ROLE_MUNICIPAL_OFFICER, null, "123456")));

        verify(userRepository, never()).save(any(User.class)); // no shortcut into the Municipal Dashboard
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void passwordSignUpCannotCreateAnAdmin() {
        assertThrows(UnauthorizedRegistrationException.class,
                () -> authService.register(registerRequest(Role.ROLE_ADMIN, null, "123456")));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void passwordSignUpStillRefusesACorporationAddress() {
        RegisterRequest request = registerRequest(Role.ROLE_CITIZEN, null, "123456");
        request.setEmail(CORPORATION_EMAIL); // someone tries to claim the city's official inbox

        when(municipalCorporationRepository.existsByEmailIgnoreCase(CORPORATION_EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class, () -> authService.register(request));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void passwordSignUpStillRequiresACleanerType() {
        noExistingAccount(TYPED_EMAIL);

        assertThrows(InvalidRegistrationException.class,
                () -> authService.register(registerRequest(Role.ROLE_CLEANER, null, "123456")));

        verifyNoInteractions(emailVerificationService);
    }

    // ---------------------------------------------------------------------
    // GOOGLE SIGN-IN
    // ---------------------------------------------------------------------

    @Test
    void anUnknownGoogleAccountIsAskedToCompleteRegistration() {
        verifiedGoogleIdentity();

        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.empty());
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(Optional.empty());

        GoogleAuthResponse response = authService.googleSignIn(googleAuthRequest());

        assertTrue(response.isRegistrationRequired());
        assertNull(response.getToken(), "nothing is authorised until the account exists");
        assertEquals(GOOGLE_EMAIL, response.getEmail()); // from the token, to prefill the form
        assertEquals(GOOGLE_NAME, response.getName());

        verify(userRepository, never()).save(any(User.class)); // the profile is still missing
        verifyNoInteractions(jwtService);
    }

    @Test
    void aLinkedGoogleAccountSignsInWithoutCreatingASecondRow() {
        verifiedGoogleIdentity();

        User citizen = citizen(GOOGLE_EMAIL);
        citizen.setGoogleSubject(GOOGLE_SUBJECT);

        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.of(citizen));
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        GoogleAuthResponse response = authService.googleSignIn(googleAuthRequest());

        assertFalse(response.isRegistrationRequired());
        assertEquals(ISSUED_TOKEN, response.getToken());
        assertEquals(Role.ROLE_CITIZEN, response.getRole());

        verify(userRepository, never()).save(any(User.class)); // nothing to write on a repeat sign-in
    }

    @Test
    void theSubjectIsWhatIdentifiesTheAccount() {
        // The address on a Google account can change; 'sub' cannot. A linked
        // account must therefore be found without the email being consulted.
        when(googleIdentityService.verify(GOOGLE_CREDENTIAL))
                .thenReturn(new GoogleIdentity(GOOGLE_SUBJECT, "renamed@example.com", GOOGLE_NAME));

        User citizen = citizen(GOOGLE_EMAIL);
        citizen.setGoogleSubject(GOOGLE_SUBJECT);

        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.of(citizen));
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        GoogleAuthResponse response = authService.googleSignIn(googleAuthRequest());

        assertEquals(GOOGLE_EMAIL, response.getEmail()); // the account's own address, not the token's
        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void anOlderPasswordAccountIsLinkedRatherThanDuplicated() {
        verifiedGoogleIdentity();

        // Registered through the old form, which stored the address as typed
        User existing = citizen("Applicant@Example.com");

        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.empty());
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(Optional.of(existing));
        when(jwtService.generateToken("Applicant@Example.com")).thenReturn(ISSUED_TOKEN);

        GoogleAuthResponse response = authService.googleSignIn(googleAuthRequest());

        assertFalse(response.isRegistrationRequired());
        assertEquals(ISSUED_TOKEN, response.getToken());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(GOOGLE_SUBJECT, saved.getValue().getGoogleSubject()); // linked, so the next sign-in matches on sub
        assertSame(existing, saved.getValue(), "the same row, not a new one");
    }

    @Test
    void aMunicipalInboxCannotBeClaimedThroughGoogle() {
        verifiedGoogleIdentity();

        // A corporation's powers come from its own row and its admin-issued
        // password; letting Google mint a personal account for the city's
        // official address would be a second, confusing identity for it
        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.empty());
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(true);

        assertThrows(InvalidCredentialsException.class,
                () -> authService.googleSignIn(googleAuthRequest()));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(jwtService);
    }

    @Test
    void aRefusedCredentialNeverReachesTheDatabase() {
        when(googleIdentityService.verify(GOOGLE_CREDENTIAL))
                .thenThrow(new InvalidCredentialsException("rejected"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.googleSignIn(googleAuthRequest()));

        verifyNoInteractions(userRepository);
        verifyNoInteractions(jwtService);
    }

    // ---------------------------------------------------------------------
    // GOOGLE REGISTRATION
    // ---------------------------------------------------------------------

    @Test
    void aVerifiedGoogleIdentityCreatesACitizen() {
        verifiedGoogleIdentity();
        noExistingAccount();

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-unusable-password");
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        GoogleAuthResponse response = authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());

        assertEquals(Role.ROLE_CITIZEN, saved.getValue().getRole());
        assertEquals(GOOGLE_SUBJECT, saved.getValue().getGoogleSubject());
        assertEquals(0, saved.getValue().getRewardPoints()); // fresh account starts at zero
        assertEquals("Punjab", saved.getValue().getState()); // normalised by LocationUtil
        assertEquals("Mohali", saved.getValue().getCity());

        assertFalse(response.isRegistrationRequired());
        assertEquals(ISSUED_TOKEN, response.getToken());
        assertEquals(Role.ROLE_CITIZEN, response.getRole()); // the JWT is issued for the real role
    }

    @Test
    void theAddressComesFromTheTokenAndNotFromTheRequest() {
        verifiedGoogleIdentity();
        noExistingAccount();

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-unusable-password");
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        // There is no email field to tamper with, which is the point: whatever
        // else the body carries, the account is created for the verified address
        authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(GOOGLE_EMAIL, saved.getValue().getEmail());
    }

    @Test
    void theStoredPasswordCanNeverBeUsedToSignIn() {
        verifiedGoogleIdentity();
        noExistingAccount();

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-unusable-password");
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null));

        // The column is NOT NULL, so something has to be stored - but it is a
        // hash of a fresh random value, not of anything a caller supplied
        ArgumentCaptor<String> encoded = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(encoded.capture());
        assertNotEquals(GOOGLE_CREDENTIAL, encoded.getValue());
        assertNotEquals(GOOGLE_EMAIL, encoded.getValue());
        assertTrue(encoded.getValue().length() >= 32, "a random value, not a guessable one");
    }

    @Test
    void municipalOfficerRoleCannotBeSelfRegistered() {
        verifiedGoogleIdentity();

        assertThrows(UnauthorizedRegistrationException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_MUNICIPAL_OFFICER, null)));

        verify(userRepository, never()).save(any(User.class)); // no shortcut into the Municipal Dashboard
    }

    @Test
    void adminRoleCannotBeSelfRegistered() {
        verifiedGoogleIdentity();

        assertThrows(UnauthorizedRegistrationException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_ADMIN, null)));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void corporationEmailCannotBeReusedForANormalAccount() {
        verifiedGoogleIdentity();

        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(true);

        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null)));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void theSameGoogleAccountCannotRegisterTwice() {
        verifiedGoogleIdentity();

        // A Google credential stays valid for about an hour, so a resubmitted
        // form would otherwise create a second account for the same person
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT))
                .thenReturn(Optional.of(citizen(GOOGLE_EMAIL)));

        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null)));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void registrationIsRefusedWhenTheAddressAlreadyHasAnAccount() {
        verifiedGoogleIdentity();

        // The address is checked before the subject, so this is refused without
        // the subject lookup ever happening
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(GOOGLE_EMAIL))
                .thenReturn(Optional.of(citizen(GOOGLE_EMAIL)));

        // googleSignIn links this case; reaching register with it must not duplicate
        assertThrows(EmailAlreadyExistsException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null)));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void cleanerMustDeclareItsCleanerType() {
        verifiedGoogleIdentity();

        // The cleaner-type rule is reached before the subject lookup
        noExistingAccount(GOOGLE_EMAIL);

        assertThrows(InvalidRegistrationException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_CLEANER, null)));
    }

    @Test
    void cleanerRegistrationWithMunicipalCrewTypeStillCreatesAPlainCleaner() {
        // Picking the MUNICIPAL crew type only describes the crew, it grants no approval powers
        verifiedGoogleIdentity();
        noExistingAccount();

        when(passwordEncoder.encode(anyString())).thenReturn("encoded-unusable-password");
        when(jwtService.generateToken(GOOGLE_EMAIL)).thenReturn(ISSUED_TOKEN);

        authService.googleRegister(googleRegisterRequest(Role.ROLE_CLEANER, CleanerType.MUNICIPAL));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(Role.ROLE_CLEANER, saved.getValue().getRole());          // still just a cleaner
        assertEquals(CleanerType.MUNICIPAL, saved.getValue().getCleanerType()); // crew type only
    }

    @Test
    void registrationIsRefusedWhenTheCredentialDoesNotVerify() {
        // The sign-in call that preceded this issued nothing, so the credential
        // is the only proof and it is checked again before any row is written
        when(googleIdentityService.verify(GOOGLE_CREDENTIAL))
                .thenThrow(new InvalidCredentialsException("rejected"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.googleRegister(googleRegisterRequest(Role.ROLE_CITIZEN, null)));

        verifyNoInteractions(userRepository);
    }

    // ---------------------------------------------------------------------
    // PASSWORD LOGIN (legacy path, unchanged)
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

    /** Google accepted the credential and the address on the account is verified. */
    private void verifiedGoogleIdentity() {
        when(googleIdentityService.verify(GOOGLE_CREDENTIAL))
                .thenReturn(new GoogleIdentity(GOOGLE_SUBJECT, GOOGLE_EMAIL, GOOGLE_NAME));
    }

    /** Neither the Google account nor the address is known to the platform yet. */
    private void noExistingAccount() {
        when(municipalCorporationRepository.existsByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(GOOGLE_EMAIL)).thenReturn(Optional.empty());
        when(userRepository.findByGoogleSubject(GOOGLE_SUBJECT)).thenReturn(Optional.empty());
    }

    /** The typed address is free, for the password sign-up path. */
    private void noExistingAccount(String email) {
        when(municipalCorporationRepository.existsByEmailIgnoreCase(email)).thenReturn(false);
        when(userRepository.findByEmailIgnoreCase(email)).thenReturn(Optional.empty());
    }

    private RegisterRequest registerRequest(Role role, CleanerType cleanerType, String code) {
        return RegisterRequest.builder()
                .name(GOOGLE_NAME)
                .email(TYPED_EMAIL)
                .password("applicant-password")
                .role(role)
                .cleanerType(cleanerType)
                .state("punjab")   // lower case on purpose: LocationUtil normalises it
                .city("mohali")
                .verificationCode(code)
                .build();
    }

    private User citizen(String email) {
        return User.builder()
                .id(1L)
                .name(GOOGLE_NAME)
                .email(email)
                .password("encoded-user-password")
                .role(Role.ROLE_CITIZEN)
                .state("Punjab")
                .city("Mohali")
                .build();
    }

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

    private GoogleAuthRequest googleAuthRequest() {
        return GoogleAuthRequest.builder()
                .credential(GOOGLE_CREDENTIAL)
                .build();
    }

    private GoogleRegisterRequest googleRegisterRequest(Role role, CleanerType cleanerType) {
        return GoogleRegisterRequest.builder()
                .credential(GOOGLE_CREDENTIAL)
                .name(GOOGLE_NAME)
                .role(role)
                .cleanerType(cleanerType)
                .state("punjab")   // lower case on purpose: LocationUtil normalises it
                .city("mohali")
                .build();
    }

    private LoginRequest loginRequest(String email, String password) {
        return LoginRequest.builder()
                .email(email)
                .password(password)
                .build();
    }
}
