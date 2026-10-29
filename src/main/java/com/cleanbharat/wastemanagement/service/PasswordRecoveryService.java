package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ConfirmPasswordResetRequest;
import com.cleanbharat.wastemanagement.dto.PasswordResetTokenResponse;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.InvalidPasswordChangeException;
import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================
 *  Profile-page password recovery
 * ============================================================
 *
 *  Lets a signed-in user who forgot their current password set a new one by
 *  proving a six-digit code sent to their registered email - no current
 *  password, no separate page. The proof of identity is the OTP, the same
 *  engine and limits the sign-up flow uses, running under a separate
 *  purpose so the two kinds of code can never stand in for each other.
 *
 *  WHO IS BEING RESET
 *  ------------------
 *  Always the authenticated session's own account, resolved the same way
 *  CustomUserDetailsService resolves it at login: a principal carrying
 *  ROLE_MUNICIPAL_OFFICER is a municipal_corporations row, anything else is
 *  a users row. The frontend supplies no email, id, or account type, so a
 *  collision between the two stores can never aim the reset at the wrong
 *  row.
 *
 *  THE TWO CREDENTIALS
 *  -------------------
 *  otp   sent by EmailVerificationService under the PASSWORD_RESET purpose;
 *        hashed in Redis, ten-minute TTL, five attempts, sixty-second
 *        resend cooldown - all inherited from that service.
 *
 *  token issued when the OTP verifies: thirty-two random bytes, returned to
 *        the caller once, and stored only as a SHA-256 hash under
 *        prt:{fingerprint} with the same ten-minute TTL. Confirming the
 *        reset spends it.
 *
 *  SPENDING THE TOKEN
 *  ------------------
 *  Atomic compare-and-delete in Redis (a Lua script: delete the stored
 *  hash only when it equals the submitted hash). A wrong token therefore
 *  cannot burn a legitimate one, and two concurrent confirmations can
 *  never both succeed - one script call deletes, the other finds nothing.
 *  Client-side "verified" flags carry no authority; the server checks its
 *  own state on every confirm.
 *
 *  NOTHING IS WRITTEN BEFORE THE PROOF
 *  -----------------------------------
 *  Password validation runs before the token is spent, so a typo in the
 *  confirmation field does not cost the user their verified reset. The
 *  password itself is stored only through the same BCrypt encoder the
 *  current-password flow uses, and the usual rule applies: it must differ
 *  from the password currently stored.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordRecoveryService {

    /** Dedicated namespace - distinct from the OTP prefixes and everything else. */
    static final String TOKEN_KEY_PREFIX = "prt:";

    /**
     * Account contexts woven into every recovery key. They come only from
     * the authenticated authority, so two accounts sharing an email across
     * the two stores - a users row and a municipal row - each get their own
     * codes, attempt counters, cooldowns and reset tokens, and neither can
     * spend the other's.
     */
    static final String CONTEXT_USER = "user";

    static final String CONTEXT_MUNICIPAL = "municipal";

    private static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);

    private static final int TOKEN_BYTES = 32;

    /*
      Atomic compare-and-delete: the stored authorization is removed only
      when it equals the submitted token hash. Returns 1 when it consumed a
      matching token, 0 otherwise.
    */
    private static final RedisScript<Long> CONSUME_TOKEN_IF_MATCH = new DefaultRedisScript<>(
            """
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            else
                return 0
            end
            """,
            Long.class
    );

    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String INVALID_TOKEN =
            "This password reset request is no longer valid. Please verify a new code.";

    private static final String CONFIRM_FAILED =
            "The password reset could not be confirmed right now. Please try again.";

    private final EmailVerificationService emailVerificationService;

    private final StringRedisTemplate redisTemplate;

    private final UserRepository userRepository;

    private final MunicipalCorporationRepository municipalCorporationRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * Sends a recovery code to the authenticated account's own email,
     * namespaced to its account context.
     *
     * @param sessionEmail     the authenticated principal's email - never a
     *                         client-supplied address
     * @param municipalSession true when the principal is a municipal
     *                         corporation (ROLE_MUNICIPAL_OFFICER), mirroring
     *                         how the account was resolved at login
     */
    public SuccessResponse requestOtp(String sessionEmail, boolean municipalSession) {

        emailVerificationService.sendCode(
                sessionEmail,
                EmailVerificationService.Purpose.PASSWORD_RESET,
                accountContext(municipalSession));

        return SuccessResponse.builder()
                .message("A six-digit recovery code has been sent to your registered email address.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Verifies the submitted code and, on success, issues the one-time reset
     * token the confirm step will spend.
     *
     * @param sessionEmail     the authenticated principal's email
     * @param municipalSession true when the principal is a municipal
     *                         corporation
     */
    public PasswordResetTokenResponse verifyOtp(String sessionEmail, boolean municipalSession, String code) {

        // Consumes the code on success and enforces the attempt cap - all
        // inside the shared OTP engine, under the PASSWORD_RESET purpose and
        // this session's account context.
        emailVerificationService.verifyCode(
                sessionEmail,
                code,
                EmailVerificationService.Purpose.PASSWORD_RESET,
                accountContext(municipalSession)
        );

        String token = generateToken();

        try {
            // Only the hash is stored; replacing any token still outstanding
            redisTemplate.opsForValue().set(
                    tokenKey(sessionEmail, municipalSession),
                    hash(token),
                    TOKEN_LIFETIME
            );
        } catch (DataAccessException ex) {
            log.error("Could not store a reset authorization: {}", ex.getClass().getSimpleName());

            // The code is already spent, so the honest answer is to ask for a
            // fresh one rather than hand out a token nobody can rely on
            throw new InvalidRegistrationException(
                    "The code could not be confirmed right now. Please request a new code and try again.");
        }

        return PasswordResetTokenResponse.builder()
                .resetToken(token)
                .message("Code verified. Set your new password.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Spends the reset token and sets the new password on the authenticated
     * account.
     *
     * @param sessionEmail    the authenticated principal's email
     * @param municipalSession true when the principal is a municipal
     *                        corporation (ROLE_MUNICIPAL_OFFICER), mirroring
     *                        how the account was resolved at login
     */
    public SuccessResponse confirmReset(
            String sessionEmail,
            boolean municipalSession,
            ConfirmPasswordResetRequest request) {

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new InvalidPasswordChangeException(
                    "New password and confirmation do not match"
            );
        }

        if (municipalSession) {
            MunicipalCorporation corporation = municipalCorporationRepository
                    .findByEmailIgnoreCase(sessionEmail)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            return spendTokenAndSetPassword(sessionEmail, municipalSession, request, corporation.getPassword(),
                    encoded -> {
                        corporation.setPassword(encoded);
                        municipalCorporationRepository.save(corporation);
                    });
        }

        User user = userRepository
                .findByEmail(sessionEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return spendTokenAndSetPassword(sessionEmail, municipalSession, request, user.getPassword(),
                encoded -> {
                    user.setPassword(encoded);
                    userRepository.save(user);
                });
    }

    /**
     * Validates the new password against the stored hash, spends the token
     * atomically, then stores the encoded password through the saver.
     *
     * Validation deliberately runs BEFORE the spend, so a typo in the
     * confirmation does not burn the user's verified reset. The spend itself
     * is the atomic compare-and-delete: an invalid token changes nothing and
     * leaves a legitimate authorization standing.
     */
    private SuccessResponse spendTokenAndSetPassword(
            String sessionEmail,
            boolean municipalSession,
            ConfirmPasswordResetRequest request,
            String storedHash,
            java.util.function.Consumer<String> passwordSaver) {

        if (storedHash == null || passwordEncoder.matches(request.getNewPassword(), storedHash)) {
            throw new InvalidPasswordChangeException(
                    "New password must be different from the current password"
            );
        }

        Long consumed;
        try {
            consumed = redisTemplate.execute(
                    CONSUME_TOKEN_IF_MATCH,
                    List.of(tokenKey(sessionEmail, municipalSession)),
                    hash(request.getResetToken())
            );
        } catch (DataAccessException ex) {
            log.error("Could not check a reset authorization: {}", ex.getClass().getSimpleName());
            throw new InvalidPasswordChangeException(CONFIRM_FAILED);
        }

        if (consumed == null || consumed != 1L) {
            throw new InvalidPasswordChangeException(INVALID_TOKEN);
        }

        passwordSaver.accept(passwordEncoder.encode(request.getNewPassword()));

        return SuccessResponse.builder()
                .message("Password reset successfully. Your new password is ready to use.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required on every JVM", ex);
        }
    }

    /**
     * Short, stable, non-reversible stand-in for an address - the same idea
     * EmailVerificationService and RateLimitService each keep privately.
     */
    private static String fingerprint(String email) {
        return hash(email.toLowerCase(Locale.ROOT)).substring(0, 16);
    }

    /** The context tag an account context maps to in Redis keys. */
    private static String accountContext(boolean municipalSession) {
        return municipalSession ? CONTEXT_MUNICIPAL : CONTEXT_USER;
    }

    /** The Redis key holding one account context's reset-token hash. */
    private static String tokenKey(String sessionEmail, boolean municipalSession) {
        return TOKEN_KEY_PREFIX + accountContext(municipalSession) + ":" + fingerprint(sessionEmail);
    }
}
