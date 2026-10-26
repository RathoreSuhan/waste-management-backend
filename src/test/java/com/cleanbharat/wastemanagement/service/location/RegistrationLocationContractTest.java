package com.cleanbharat.wastemanagement.service.location;

import com.cleanbharat.wastemanagement.controller.AuthController;
import com.cleanbharat.wastemanagement.controller.LocationController;
import com.cleanbharat.wastemanagement.dto.GoogleRegisterRequest;
import com.cleanbharat.wastemanagement.dto.RegisterRequest;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.GlobalExceptionHandler;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.security.JwtService;
import com.cleanbharat.wastemanagement.service.AuthService;
import com.cleanbharat.wastemanagement.service.EmailVerificationService;
import com.cleanbharat.wastemanagement.service.GoogleIdentityService;
import com.cleanbharat.wastemanagement.service.GoogleIdentityService.GoogleIdentity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for registration's location boundary.
 *
 * Standalone MockMvc is deliberate: the real controllers, validation advice,
 * AuthService and generated LGD directory run, while persistence, email,
 * Google verification, password hashing and JWT issuance stay mocked. These
 * tests therefore cannot touch PostgreSQL, Redis, SMTP or Google.
 */
class RegistrationLocationContractTest {

    private static final String EMAIL = "location.contract@example.com";
    private static final String GOOGLE_CREDENTIAL = "verified-google-credential";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserRepository userRepository;
    private MunicipalCorporationRepository municipalCorporationRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private GoogleIdentityService googleIdentityService;
    private EmailVerificationService emailVerificationService;
    private IndianLocationService locationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        municipalCorporationRepository = mock(MunicipalCorporationRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtService = mock(JwtService.class);
        googleIdentityService = mock(GoogleIdentityService.class);
        emailVerificationService = mock(EmailVerificationService.class);

        locationService = new IndianLocationService(objectMapper);
        locationService.load();

        mockMvc = mvcFor(locationService);
    }

    @Test
    void thePublicDirectoryContractCarriesTheGeneratedTreeAndOneDayCache() throws Exception {
        mockMvc.perform(get("/api/locations"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=86400, public"))
                .andExpect(jsonPath("$.counts.states").value(35))
                .andExpect(jsonPath("$.counts.districts").value(752))
                .andExpect(jsonPath("$.counts.ulbs").value(5059))
                .andExpect(jsonPath("$.states[?(@.code == '10')].name").value("Bihar"));
    }

    @ParameterizedTest(name = "refuses state={0}, district={1}, ulb={2}")
    @MethodSource("invalidTriples")
    void invalidLocationTriplesAre400BeforeAnyEmailOrAccountWrite(
            String stateCode,
            String districtCode,
            String ulbCode,
            String messageFragment
    ) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                passwordRequest(stateCode, districtCode, ulbCode, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString(messageFragment)));

        verifyNoInteractions(emailVerificationService);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void missingCodesAreRejectedByTheRequestContractBeforeTheService() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Location Contract",
                                  "email": "location.contract@example.com",
                                  "password": "password-123",
                                  "role": "ROLE_CITIZEN"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        verifyNoInteractions(emailVerificationService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void validBiharGayaReachesTheOtpStepWithoutCreatingAnAccount() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                passwordRequest("10", "196", "249680", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationRequired").value(true))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());

        verify(emailVerificationService).sendCode(EMAIL);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void theCreateCallRevalidatesChangedCodesBeforeCheckingTheOtp() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                passwordRequest("3", "196", "249680", "123456"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Bihar")));

        verify(emailVerificationService, never()).verifyCode(anyString(), anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void googleRegistrationRejectsTheSameInvalidTriple() throws Exception {
        verifiedGoogleIdentity();

        mockMvc.perform(post("/api/auth/google/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                googleRequest("3", "196", "249680"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message", containsString("Bihar")));

        verify(userRepository, never()).save(any(User.class));
        verifyNoInteractions(passwordEncoder, jwtService);
    }

    @Test
    void validGoogleRegistrationStoresOnlyCanonicalStateAndCityNames() throws Exception {
        verifiedGoogleIdentity();
        when(passwordEncoder.encode(anyString())).thenReturn("unusable-password-hash");
        when(jwtService.generateToken(EMAIL)).thenReturn("clean-bharat-jwt");
        when(userRepository.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/auth/google/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                googleRequest("10", "196", "249680"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationRequired").value(false))
                .andExpect(jsonPath("$.token").value("clean-bharat-jwt"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("ROLE_CITIZEN"));

        var saved = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("Bihar", saved.getValue().getState());
        assertEquals("Gaya", saved.getValue().getCity());
    }

    @Test
    void anUnavailableDirectoryIs503ForBothReadingAndRegistration() throws Exception {
        IndianLocationService unavailable = new IndianLocationService(objectMapper);
        MockMvc unavailableMvc = mvcFor(unavailable);

        unavailableMvc.perform(get("/api/locations"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.message", containsString("temporarily unavailable")));

        unavailableMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                passwordRequest("10", "196", "249680", null))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));

        verifyNoInteractions(emailVerificationService);
        verify(userRepository, never()).save(any(User.class));
    }

    private MockMvc mvcFor(IndianLocationService directory) {
        AuthService authService = new AuthService(
                userRepository,
                municipalCorporationRepository,
                passwordEncoder,
                jwtService,
                googleIdentityService,
                emailVerificationService,
                directory
        );

        return MockMvcBuilders
                .standaloneSetup(
                        new LocationController(directory),
                        new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void verifiedGoogleIdentity() {
        when(googleIdentityService.verify(GOOGLE_CREDENTIAL))
                .thenReturn(new GoogleIdentity("google-subject", EMAIL, "Location Contract"));
    }

    private RegisterRequest passwordRequest(
            String stateCode,
            String districtCode,
            String ulbCode,
            String verificationCode
    ) {
        return RegisterRequest.builder()
                .name("Location Contract")
                .email(EMAIL)
                .password("password-123")
                .role(Role.ROLE_CITIZEN)
                .stateCode(stateCode)
                .districtCode(districtCode)
                .ulbCode(ulbCode)
                .verificationCode(verificationCode)
                .build();
    }

    private GoogleRegisterRequest googleRequest(
            String stateCode,
            String districtCode,
            String ulbCode
    ) {
        return GoogleRegisterRequest.builder()
                .credential(GOOGLE_CREDENTIAL)
                .name("Location Contract")
                .role(Role.ROLE_CITIZEN)
                .stateCode(stateCode)
                .districtCode(districtCode)
                .ulbCode(ulbCode)
                .build();
    }

    private static Stream<Arguments> invalidTriples() {
        return Stream.of(
                Arguments.of("3", "196", "249680", "Bihar"),
                Arguments.of("10", "196", "248273", "not in Bihar"),
                Arguments.of("10", "27", "248273", "Punjab"),
                Arguments.of("10", "196", "196", "not listed"),
                Arguments.of("10", "196", "999999", "not listed"),
                Arguments.of("10", "999", "249680", "not in Bihar"),
                Arguments.of("999", "196", "249680", "not recognised")
        );
    }
}
