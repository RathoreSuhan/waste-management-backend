package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ConfirmPasswordResetRequest;
import com.cleanbharat.wastemanagement.dto.PasswordResetTokenResponse;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the profile-page password recovery flow.
 *
 * What is pinned here: the OTP always goes to the authenticated account's
 * own email; verifying it issues a single-use token stored only as a hash;
 * confirming spends that token atomically and stores a BCrypt-encoded
 * password on the account the session actually authenticated as - a users
 * row for citizens/cleaners/admins, the municipal_corporations row for an
 * officer session, never a mixture, never the other store. Every failure
 * path - wrong code, expired code, invalid or spent token, mismatched
 * confirmation, unchanged password - leaves the stored password untouched.
 */
@ExtendWith(MockitoExtension.class)
class PasswordRecoveryServiceTest {

    private static final String EMAIL = "citizen@example.com";

    private static final String OLD_HASH = "bcrypt-old-hash";

    private static final String NEW_ENCODED = "bcrypt-new-encoded-hash";

    @Mock private EmailVerificationService emailVerificationService;

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private UserRepository userRepository;

    @Mock private MunicipalCorporationRepository municipalCorporationRepository;

    @Mock private PasswordEncoder passwordEncoder;

    private PasswordRecoveryService passwordRecoveryService;

    @BeforeEach
    void setUp() {
        passwordRecoveryService = new PasswordRecoveryService(
                emailVerificationService,
                redisTemplate,
                userRepository,
                municipalCorporationRepository,
                passwordEncoder
        );
    }

    private ConfirmPasswordResetRequest confirmRequest(String token, String newPassword) {
        return new ConfirmPasswordResetRequest(token, newPassword, newPassword);
    }

    private void stubTokenConsumption(Long result) {
        when(redisTemplate.execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                any())).thenReturn(result);
    }

    private static String tokenKey(String email, boolean municipalSession) {
        // Mirrors the production fingerprint: half a SHA-256 of the lower-cased address
        return "prt:" + (municipalSession ? "municipal" : "user") + ":" + sha256Hex(email.toLowerCase()).substring(0, 16);
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ send

    @Test
    void theOtpGoesOnlyToTheAuthenticatedAccountEmail() {
        SuccessResponse response = passwordRecoveryService.requestOtp(EMAIL, false);

        // The service takes no address parameter: the code can only ever go
        // to the authenticated session's own email, namespaced to its context
        verify(emailVerificationService).sendCode(
                eq(EMAIL), eq(EmailVerificationService.Purpose.PASSWORD_RESET), eq("user"));
        assertTrue(response.getMessage().contains("recovery code"));
    }

    @Test
    void anOfficerSessionSendsUnderItsOwnAccountContext() {
        passwordRecoveryService.requestOtp(EMAIL, true);

        verify(emailVerificationService).sendCode(
                eq(EMAIL), eq(EmailVerificationService.Purpose.PASSWORD_RESET), eq("municipal"));
    }

    @Test
    void aCollidingEmailAcrossStoresKeepsEachAccountsRecoverySeparate() {
        // The exact collision scenario: one email, one users row, one
        // municipal row, two independent authenticated sessions

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // Each side verifies its own OTP and gets its own token, under its
        // own context key - the second verify cannot overwrite the first
        PasswordResetTokenResponse officerToken = passwordRecoveryService.verifyOtp(EMAIL, true, "111111");
        verify(valueOperations).set(
                eq(tokenKey(EMAIL, true)),
                eq(sha256Hex(officerToken.getResetToken())),
                any(java.time.Duration.class));

        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        PasswordResetTokenResponse citizenToken = passwordRecoveryService.verifyOtp(EMAIL, false, "222222");
        verify(valueOperations).set(
                eq(tokenKey(EMAIL, false)),
                eq(sha256Hex(citizenToken.getResetToken())),
                any(java.time.Duration.class));

        // The citizen now submits the OFFICER's token. The confirm consults
        // only the user-context key, which holds the citizen's own hash - no
        // match, no consumption, and the users row is never touched
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        stubTokenConsumption(0L);

        assertThrows(InvalidPasswordChangeException.class,
                () -> passwordRecoveryService.confirmReset(
                        EMAIL, false, confirmRequest(officerToken.getResetToken(), "new-password")));

        verify(redisTemplate, atLeastOnce()).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(List.of(tokenKey(EMAIL, false))),
                any());
        verify(userRepository, never()).save(any(User.class));

        // The officer still spends their own token on the corporation row
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);
        stubTokenConsumption(1L);
        MunicipalCorporation corporation = MunicipalCorporation.builder()
                .id(5L)
                .email(EMAIL)
                .password(OLD_HASH)
                .build();
        when(municipalCorporationRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(corporation));

        passwordRecoveryService.confirmReset(
                EMAIL, true, confirmRequest(officerToken.getResetToken(), "new-password"));

        verify(redisTemplate, atLeastOnce()).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.eq(List.of(tokenKey(EMAIL, true))),
                any());
        assertEquals(NEW_ENCODED, corporation.getPassword());
    }

    // ---------------------------------------------------------------- verify

    @Test
    void aVerifiedOtpIssuesASingleUseTokenStoredOnlyAsAHash() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        PasswordResetTokenResponse response = passwordRecoveryService.verifyOtp(EMAIL, false, "123456");

        verify(emailVerificationService).verifyCode(
                eq(EMAIL), eq("123456"), eq(EmailVerificationService.Purpose.PASSWORD_RESET), eq("user"));

        assertNotNull(response.getResetToken());

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(key.capture(), stored.capture(), any(java.time.Duration.class));

        // Keyed to the account AND its context, never containing the
        // address or the token
        assertEquals(tokenKey(EMAIL, false), key.getValue());
        assertEquals(sha256Hex(response.getResetToken()), stored.getValue());
    }

    @Test
    void anIncorrectOtpIssuesNoToken() {
        org.mockito.Mockito.doThrow(new InvalidRegistrationException("That code is not correct. Please check it and try again."))
                .when(emailVerificationService).verifyCode(
                        eq(EMAIL), eq("000000"), eq(EmailVerificationService.Purpose.PASSWORD_RESET), eq("user"));

        assertThrows(InvalidRegistrationException.class,
                () -> passwordRecoveryService.verifyOtp(EMAIL, false, "000000"));

        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void anExpiredOtpIssuesNoToken() {
        org.mockito.Mockito.doThrow(new InvalidRegistrationException("That code has expired. Please request a new one."))
                .when(emailVerificationService).verifyCode(
                        eq(EMAIL), eq("999999"), eq(EmailVerificationService.Purpose.PASSWORD_RESET), eq("user"));

        assertThrows(InvalidRegistrationException.class,
                () -> passwordRecoveryService.verifyOtp(EMAIL, false, "999999"));

        verify(redisTemplate, never()).opsForValue();
    }

    // --------------------------------------------------------------- confirm

    @Test
    void aValidTokenResetsThePasswordWithoutTheCurrentOne() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        SuccessResponse response =
                passwordRecoveryService.confirmReset(EMAIL, false, confirmRequest("token-1", "new-password"));

        assertEquals(NEW_ENCODED, user.getPassword());
        verify(userRepository).save(user);
        assertTrue(response.getMessage().contains("reset successfully"));
    }

    @Test
    void theOldPasswordNoLongerAuthenticatesAfterTheReset() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        passwordRecoveryService.confirmReset(EMAIL, false, confirmRequest("token-1", "new-password"));

        // The row now carries a different hash, and the new one is not a
        // second copy of the old
        assertNotEquals(OLD_HASH, user.getPassword());
        verify(passwordEncoder).encode("new-password");
    }

    @Test
    void anOfficerSessionResetsTheCorporationRowAndNeverTheUsersRow() {
        // Deliberate collision: the same email exists in both stores, and the
        // session is a municipal-officer session, so only the corporation row
        // may change
        MunicipalCorporation corporation = MunicipalCorporation.builder()
                .id(5L)
                .email(EMAIL)
                .password(OLD_HASH)
                .build();
        when(municipalCorporationRepository.findByEmailIgnoreCase(EMAIL))
                .thenReturn(Optional.of(corporation));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        passwordRecoveryService.confirmReset(EMAIL, true, confirmRequest("token-2", "new-password"));

        assertEquals(NEW_ENCODED, corporation.getPassword());
        verify(municipalCorporationRepository).save(corporation);
        verify(userRepository, never()).findByEmail(anyString());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void aCitizenSessionResetsTheUsersRowAndNeverTheCorporationRow() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        passwordRecoveryService.confirmReset(EMAIL, false, confirmRequest("token-3", "new-password"));

        verify(userRepository).save(user);
        verify(municipalCorporationRepository, never()).findByEmailIgnoreCase(anyString());
        verify(municipalCorporationRepository, never()).save(any(MunicipalCorporation.class));
    }

    @Test
    void anInvalidTokenChangesNothingAndLeavesALegitimateOneUsable() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        // First submission presents the wrong token: the script answers 0 -
        // nothing consumed, nothing mutated
        stubTokenConsumption(0L);

        assertThrows(InvalidPasswordChangeException.class,
                () -> passwordRecoveryService.confirmReset(
                        EMAIL, false, confirmRequest("wrong-token", "new-password")));

        verify(userRepository, never()).save(any(User.class));

        // The correct token still works afterwards: the failed guess did not
        // burn the legitimate reset authorization
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        passwordRecoveryService.confirmReset(EMAIL, false, confirmRequest("right-token", "new-password"));

        verify(userRepository).save(user);
    }

    @Test
    void aConsumedTokenCannotAuthorizeASecondReset() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(false);
        // The script consumes on the first confirm and finds nothing after
        stubTokenConsumption(1L);
        when(passwordEncoder.encode("new-password")).thenReturn(NEW_ENCODED);

        passwordRecoveryService.confirmReset(EMAIL, false, confirmRequest("token", "new-password"));

        stubTokenConsumption(0L);
        assertThrows(InvalidPasswordChangeException.class,
                () -> passwordRecoveryService.confirmReset(
                        EMAIL, false, confirmRequest("token", "another-password")));

        // Exactly one password write: the replay changed nothing
        verify(userRepository, times(1)).save(user);
    }

    @Test
    void aMismatchedConfirmationChangesNothing() {
        // The confirmation check is the first thing confirm does, so no
        // account lookup, no token spend, and no write can happen

        ConfirmPasswordResetRequest mismatched = new ConfirmPasswordResetRequest(
                "token", "new-password", "different-password");

        InvalidPasswordChangeException refused = assertThrows(
                InvalidPasswordChangeException.class,
                () -> passwordRecoveryService.confirmReset(EMAIL, false, mismatched));

        assertTrue(refused.getMessage().contains("do not match"));
        // Validation runs before the token is spent, so the user can retry
        verify(redisTemplate, never()).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                any());
        verify(userRepository, never()).save(any(User.class));
        verify(municipalCorporationRepository, never()).save(any(MunicipalCorporation.class));
    }

    @Test
    void theNewPasswordMustDifferFromTheStoredOne() {
        User user = User.builder()
                .id(1L)
                .email(EMAIL)
                .role(Role.ROLE_CITIZEN)
                .password(OLD_HASH)
                .build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("new-password", OLD_HASH)).thenReturn(true);

        InvalidPasswordChangeException refused = assertThrows(
                InvalidPasswordChangeException.class,
                () -> passwordRecoveryService.confirmReset(
                        EMAIL, false, confirmRequest("token", "new-password")));

        assertTrue(refused.getMessage().contains("different from the current password"));
        verify(userRepository, never()).save(any(User.class));

        // Order guarantee: the policy check runs BEFORE the token spend, so
        // a rejected password never burns the valid reset authorization
        verify(redisTemplate, never()).execute(
                org.mockito.ArgumentMatchers.<RedisScript<Long>>any(),
                org.mockito.ArgumentMatchers.anyList(),
                any());
    }
}
