package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * ============================================================
 *  Sign-up email verification
 * ============================================================
 *
 *  Proves that whoever filled in the sign-up form can read mail at the address
 *  they typed, by sending a six-digit code there and asking for it back.
 *
 *  WHY A CODE AND NOT A LOOKUP
 *  ---------------------------
 *  There is no way to ask Google, Yahoo or Microsoft whether an address exists.
 *  Those lookups were withdrawn precisely because they let anyone enumerate
 *  accounts, and probing a mail server directly is both blocked by the large
 *  providers and treated as abuse. Delivering something to the mailbox is the
 *  only check that works everywhere - Gmail, Yahoo, Outlook, a college domain -
 *  and it is the only one that proves ownership rather than mere existence.
 *
 *  WHERE THE CODE LIVES
 *  --------------------
 *  In the Redis this application already runs, reusing the autoconfigured
 *  StringRedisTemplate that RateLimitService sits on - one connection, no new
 *  table, and the key expires on its own so there is nothing to clean up.
 *
 *      evc:{fingerprint}   SHA-256 of the code, 10 minute TTL
 *      eva:{fingerprint}   attempts spent against that code
 *
 *  Both prefixes are distinct from RateLimitService's "rl:" and from the cache
 *  manager's "cacheName::key", so nothing here can collide with a rate-limit
 *  bucket or evict a cached value.
 *
 *  WHAT IS STORED
 *  --------------
 *  Never the code itself, and never the address. The code is hashed, so a Redis
 *  dump hands out nothing usable; the address is fingerprinted for the same
 *  reason RateLimitService fingerprints its identities - the key still points at
 *  exactly one person without recording who.
 *
 *  THE LIMITS, AND WHY EACH ONE IS THERE
 *  -------------------------------------
 *  Ten minutes      long enough to find the mail, short enough that a code read
 *                   off an old screen is useless.
 *  Five attempts    a six-digit code is one in a million, which is only strong
 *                   while guesses are capped. Unlimited tries inside ten minutes
 *                   is not.
 *  Sixty seconds    between sends, so this cannot be used to flood somebody
 *                   else's inbox, and so one sign-up cannot drain the mail quota.
 *
 *  FAILING CLOSED
 *  --------------
 *  With no SMTP account configured, or if the send fails, sign-up is refused.
 *  There is deliberately no "log the code to the console instead" developer
 *  shortcut: that is an unverified-account backdoor one missing environment
 *  variable away from production.
 * ============================================================
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    /** Dedicated namespaces. Nothing else in the application writes these. */
    static final String CODE_KEY_PREFIX = "evc:";

    static final String ATTEMPT_KEY_PREFIX = "eva:";

    private static final Duration CODE_LIFETIME = Duration.ofMinutes(10);

    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);

    private static final int MAX_ATTEMPTS = 5;

    /** Half a SHA-256 is 64 bits - collision-free at this scale, and short. */
    private static final int FINGERPRINT_LENGTH = 16;

    /*
      SecureRandom, not Random. This code is a credential for the length of its
      life, and java.util.Random's sequence is reconstructable from a couple of
      observed outputs - which an attacker can collect simply by signing up.
    */
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String NOT_CONFIGURED =
            "Email verification is not available on this server right now. Please try again later, or continue with Google.";

    private static final String SEND_FAILED =
            "We could not send the verification code. Please check the address and try again.";

    private static final String NO_CODE =
            "That code has expired. Please request a new one.";

    private static final String WRONG_CODE =
            "That code is not correct. Please check it and try again.";

    private static final String TOO_MANY_ATTEMPTS =
            "Too many incorrect codes were entered. Please request a new one.";

    private final StringRedisTemplate redisTemplate;

    private final JavaMailSender mailSender;

    @Value("${mail.username:}")
    private String username;

    // Gmail rejects a From that is not the authenticated account, so it defaults to it
    @Value("${mail.from:}")
    private String from;

    /**
     * Sends a fresh code to the address, replacing any code already outstanding.
     *
     * @throws InvalidRegistrationException if mail is unconfigured, another code
     *         was sent less than a minute ago, or the message could not be sent
     */
    public void sendCode(String email) {

        if (!StringUtils.hasText(username)) {
            log.error("MAIL_USERNAME is not set, so sign-up verification codes cannot be sent.");

            throw new InvalidRegistrationException(NOT_CONFIGURED);
        }

        String fingerprint = fingerprint(email);

        long waitSeconds = secondsUntilResendAllowed(fingerprint);

        if (waitSeconds > 0) {
            throw new InvalidRegistrationException(
                    "A code was just sent. Please wait " + waitSeconds
                            + " seconds before asking for another.");
        }

        String code = generateCode();

        /*
          Written before the message goes out, so a Redis failure costs nothing
          and is caught before an email is spent. If the send then fails, both
          keys are removed below - otherwise the cooldown would block a retry of
          a code that never arrived.
        */
        try {
            redisTemplate.opsForValue().set(
                    CODE_KEY_PREFIX + fingerprint,
                    hash(code),
                    CODE_LIFETIME
            );

            redisTemplate.delete(ATTEMPT_KEY_PREFIX + fingerprint);

        } catch (DataAccessException ex) {
            /*
              Fails closed, unlike RateLimitService which deliberately fails
              open. A limiter that cannot count should let traffic through; a
              verification that cannot be recorded must not create an account.
            */
            log.error("Could not store a verification code: {}", ex.getClass().getSimpleName());

            throw new InvalidRegistrationException(SEND_FAILED);
        }

        try {
            mailSender.send(buildMessage(email, code));

        } catch (Exception ex) {
            discard(fingerprint); // nothing arrived, so nothing should block a retry

            // The address is not logged: an unsent sign-up is still somebody's address
            log.warn("Could not send a verification code: {}", ex.getClass().getSimpleName());

            throw new InvalidRegistrationException(SEND_FAILED);
        }
    }

    /**
     * Checks a submitted code and consumes it.
     *
     * A correct code is removed on use, so one code creates at most one account.
     *
     * @throws InvalidRegistrationException if the code is missing, expired,
     *         wrong, or too many wrong codes have already been tried
     */
    public void verifyCode(String email, String code) {

        String fingerprint = fingerprint(email);

        String expected;

        Long attempts;

        try {
            expected = redisTemplate.opsForValue().get(CODE_KEY_PREFIX + fingerprint);

            if (expected == null) {
                throw new InvalidRegistrationException(NO_CODE);
            }

            /*
              Counted before the comparison, so a guess costs an attempt whether
              or not it was right. Incrementing only on failure would let someone
              probe freely by watching which replies came back slower.
            */
            attempts = redisTemplate.opsForValue().increment(ATTEMPT_KEY_PREFIX + fingerprint);

            // The counter is created by the increment above, so it needs its own expiry
            redisTemplate.expire(ATTEMPT_KEY_PREFIX + fingerprint, CODE_LIFETIME);

        } catch (DataAccessException ex) {
            log.error("Could not read a verification code: {}", ex.getClass().getSimpleName());

            throw new InvalidRegistrationException(SEND_FAILED);
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {
            discard(fingerprint); // burnt: a new code has to be requested

            throw new InvalidRegistrationException(TOO_MANY_ATTEMPTS);
        }

        // Constant-time: a byte-by-byte comparison leaks how much of a guess matched
        boolean matches = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                hash(code).getBytes(StandardCharsets.UTF_8)
        );

        if (!matches) {
            throw new InvalidRegistrationException(WRONG_CODE);
        }

        discard(fingerprint); // one code, one account
    }

    /**
     * Seconds left on the resend cooldown, or 0 when a new code may be sent.
     *
     * Read from the code key's own remaining TTL rather than a second key: a
     * code with more than nine minutes left was necessarily issued less than a
     * minute ago.
     */
    private long secondsUntilResendAllowed(String fingerprint) {

        try {
            Long ttl = redisTemplate.getExpire(CODE_KEY_PREFIX + fingerprint, TimeUnit.SECONDS);

            if (ttl == null || ttl <= 0) {
                return 0; // no code outstanding, or no expiry to read
            }

            long cooldownEndsAt = CODE_LIFETIME.minus(RESEND_COOLDOWN).toSeconds();

            return Math.max(0, ttl - cooldownEndsAt);

        } catch (DataAccessException ex) {
            // Unknown rather than blocked: the send below fails closed anyway
            return 0;
        }
    }

    /** Removes a code and its attempt counter, ignoring a Redis that is unwell. */
    private void discard(String fingerprint) {
        try {
            redisTemplate.delete(CODE_KEY_PREFIX + fingerprint);
            redisTemplate.delete(ATTEMPT_KEY_PREFIX + fingerprint);
        } catch (DataAccessException ex) {
            // The keys expire on their own, so this is not worth failing over
            log.warn("Could not clear a verification code: {}", ex.getClass().getSimpleName());
        }
    }

    private SimpleMailMessage buildMessage(String email, String code) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(StringUtils.hasText(from) ? from : username);
        message.setTo(email);

        /*
          The code is deliberately NOT in the subject line. Putting it there is
          common and does save a tap, but it also renders the code on a locked
          screen in a notification preview, where somebody else can read it.
        */
        message.setSubject("Verify your Clean Bharat email address");

        // Both languages, as everywhere else a person is addressed directly
        message.setText("""
                Clean Bharat - email verification

                Your verification code is: %s

                It expires in 10 minutes. If you did not try to create a Clean
                Bharat account, you can ignore this message - no account has
                been created and nothing further will happen.

                ----------------------------------------------------------

                स्वच्छ भारत - ईमेल सत्यापन

                आपका सत्यापन कोड है: %s

                यह कोड 10 मिनट में समाप्त हो जाएगा। यदि आपने स्वच्छ भारत खाता
                बनाने का प्रयास नहीं किया है, तो इस संदेश की अनदेखी करें - कोई
                खाता नहीं बनाया गया है।
                """.formatted(code, code));

        return message;
    }

    /** Six digits, zero padded, from a cryptographically strong source. */
    private static String generateCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String hash(String value) {
        return toHex(digest(value), Integer.MAX_VALUE);
    }

    /**
     * Short, stable, non-reversible stand-in for an address.
     *
     * Deliberately a copy of the same idea in RateLimitService rather than a
     * shared helper: that method is private to a security-critical class whose
     * tests pin its exact key shape, and reaching into it to save fifteen lines
     * is not worth changing a file this one does not otherwise touch.
     */
    private static String fingerprint(String email) {
        return toHex(digest(email.toLowerCase(Locale.ROOT)), FINGERPRINT_LENGTH);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required on every JVM", ex);
        }
    }

    private static String toHex(byte[] digest, int maxHexChars) {

        int bytes = Math.min(digest.length, maxHexChars / 2);

        StringBuilder hex = new StringBuilder(bytes * 2);

        for (int i = 0; i < bytes; i++) {
            hex.append(String.format("%02x", digest[i]));
        }

        return hex.toString();
    }
}
