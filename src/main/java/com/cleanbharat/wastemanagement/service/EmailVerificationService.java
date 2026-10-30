package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /**
     * What a code authorizes. Purposes are namespaced apart in Redis, so a
     * sign-up code can never authorize a password reset and vice versa -
     * the two lookups simply read different keys.
     */
    public enum Purpose {

        /** Proves mailbox ownership at sign-up. Legacy prefixes kept stable. */
        REGISTRATION("evc:", "eva:", "Verify your Clean Bharat email address"),

        /** Authorizes the profile-page password recovery flow. */
        PASSWORD_RESET("prc:", "pra:", "Reset your Clean Bharat password");

        public final String codeKeyPrefix;

        public final String attemptKeyPrefix;

        public final String emailSubject;

        Purpose(String codeKeyPrefix, String attemptKeyPrefix, String emailSubject) {
            this.codeKeyPrefix = codeKeyPrefix;
            this.attemptKeyPrefix = attemptKeyPrefix;
            this.emailSubject = emailSubject;
        }
    }

    /** Registration's namespaces, kept as constants for the existing tests. */
    static final String CODE_KEY_PREFIX = Purpose.REGISTRATION.codeKeyPrefix;

    static final String ATTEMPT_KEY_PREFIX = Purpose.REGISTRATION.attemptKeyPrefix;

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

    /*
      Render free tier blocks outbound SMTP ports 25/465/587 entirely (since Sep 2025),
      so Gmail SMTP always fails there even with correct env vars. Brevo's HTTPS API
      (port 443) is the free-tier-compatible path. When set, it is preferred over SMTP.
      Env: BREVO_API_KEY (secret), BREVO_API_URL optional, BREVO_SENDER_NAME optional.
    */
    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.api-url:https://api.brevo.com/v3/smtp/email}")
    private String brevoApiUrl;

    @Value("${brevo.sender-name:Clean Bharat}")
    private String brevoSenderName;

    /**
     * Sends a fresh sign-up code to the address, replacing any code already
     * outstanding.
     *
     * @throws InvalidRegistrationException if mail is unconfigured, another code
     *         was sent less than a minute ago, or the message could not be sent
     */
    public void sendCode(String email) {
        sendCode(email, Purpose.REGISTRATION, null);
    }

    /**
     * Sends a fresh code for the given purpose without an account context,
     * replacing any code already outstanding for that purpose.
     */
    public void sendCode(String email, Purpose purpose) {
        sendCode(email, purpose, null);
    }

    /**
     * Sends a fresh code for the given purpose, scoped to the given account
     * context, replacing any code already outstanding for that purpose and
     * context.
     *
     * Purposes keep separate cooldowns: a password-reset send is never
     * blocked by a recent sign-up send. The context separates accounts that
     * share an address across stores - a municipal officer and a users-row
     * account with the same email each get their own code, attempts and
     * cooldown, and neither can consume the other's.
     *
     * @param keyContext optional account-context tag (e.g. "user",
     *        "municipal") woven into the Redis keys; derived by the caller
     *        from the authenticated authority, never from client input.
     *        Null keeps the legacy key shape (registration).
     */
    public void sendCode(String email, Purpose purpose, String keyContext) {

        if (!StringUtils.hasText(username)) {
            log.error("MAIL_USERNAME is not set, so verification codes cannot be sent.");

            throw new InvalidRegistrationException(NOT_CONFIGURED);
        }

        String fingerprint = fingerprint(email);

        long waitSeconds = secondsUntilResendAllowed(fingerprint, purpose, keyContext);

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
                    codeKey(purpose, keyContext, fingerprint),
                    hash(code),
                    CODE_LIFETIME
            );

            redisTemplate.delete(attemptKey(purpose, keyContext, fingerprint));

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
            if (StringUtils.hasText(brevoApiKey)) {
                // HTTPS/443 - works on Render free tier where SMTP ports are blocked
                sendViaBrevo(email, purpose.emailSubject, buildTextBody(purpose, code));
            } else {
                mailSender.send(buildMessage(email, purpose.emailSubject, buildTextBody(purpose, code)));
            }

        } catch (Exception ex) {
            discard(fingerprint, purpose, keyContext); // nothing arrived, so nothing should block a retry

            // Address/code never logged; root cause kept for Render log diagnosis (no secrets)
            log.warn("Could not send a verification code: {} cause={} msg={}",
                    ex.getClass().getSimpleName(),
                    rootCauseName(ex),
                    rootCauseMessage(ex));

            throw new InvalidRegistrationException(SEND_FAILED);
        }
    }

    /**
     * Checks a submitted sign-up code and consumes it.
     *
     * A correct code is removed on use, so one code creates at most one account.
     *
     * @throws InvalidRegistrationException if the code is missing, expired,
     *         wrong, or too many wrong codes have already been tried
     */
    public void verifyCode(String email, String code) {
        verifyCode(email, code, Purpose.REGISTRATION, null);
    }

    /**
     * Checks a submitted code for the given purpose without an account
     * context and consumes it.
     */
    public void verifyCode(String email, String code, Purpose purpose) {
        verifyCode(email, code, purpose, null);
    }

    /**
     * Checks a submitted code for the given purpose and account context and
     * consumes it.
     *
     * Only codes sent for this purpose AND context are read - a password-
     * reset code can never authorize a sign-up, a sign-up code can never
     * authorize a password reset, and a code verified for one account store
     * can never authorize work against the other store, even when both
     * accounts share an email address.
     */
    public void verifyCode(String email, String code, Purpose purpose, String keyContext) {

        String fingerprint = fingerprint(email);

        String expected;

        Long attempts;

        try {
            expected = redisTemplate.opsForValue().get(codeKey(purpose, keyContext, fingerprint));

            if (expected == null) {
                throw new InvalidRegistrationException(NO_CODE);
            }

            /*
              Counted before the comparison, so a guess costs an attempt whether
              or not it was right. Incrementing only on failure would let someone
              probe freely by watching which replies came back slower.
            */
            attempts = redisTemplate.opsForValue().increment(attemptKey(purpose, keyContext, fingerprint));

            // The counter is created by the increment above, so it needs its own expiry
            redisTemplate.expire(attemptKey(purpose, keyContext, fingerprint), CODE_LIFETIME);

        } catch (DataAccessException ex) {
            log.error("Could not read a verification code: {}", ex.getClass().getSimpleName());

            throw new InvalidRegistrationException(SEND_FAILED);
        }

        if (attempts != null && attempts > MAX_ATTEMPTS) {
            discard(fingerprint, purpose, keyContext); // burnt: a new code has to be requested

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

        discard(fingerprint, purpose, keyContext); // one code, one use
    }

    /** The Redis key holding a purpose/context's code hash. */
    private static String codeKey(Purpose purpose, String keyContext, String fingerprint) {
        return purpose.codeKeyPrefix + keyContextSuffix(keyContext) + fingerprint;
    }

    /** The Redis key counting verification attempts for one purpose/context. */
    private static String attemptKey(Purpose purpose, String keyContext, String fingerprint) {
        return purpose.attemptKeyPrefix + keyContextSuffix(keyContext) + fingerprint;
    }

    /** Null contexts (registration) keep the legacy key shape exactly. */
    private static String keyContextSuffix(String keyContext) {
        return StringUtils.hasText(keyContext) ? keyContext + ":" : "";
    }

    /**
     * Seconds left on the resend cooldown, or 0 when a new code may be sent.
     *
     * Read from the code key's own remaining TTL rather than a second key: a
     * code with more than nine minutes left was necessarily issued less than a
     * minute ago.
     */
    private long secondsUntilResendAllowed(String fingerprint, Purpose purpose, String keyContext) {

        try {
            Long ttl = redisTemplate.getExpire(codeKey(purpose, keyContext, fingerprint), TimeUnit.SECONDS);

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
    private void discard(String fingerprint, Purpose purpose, String keyContext) {
        try {
            redisTemplate.delete(codeKey(purpose, keyContext, fingerprint));
            redisTemplate.delete(attemptKey(purpose, keyContext, fingerprint));
        } catch (DataAccessException ex) {
            // The keys expire on their own, so this is not worth failing over
            log.warn("Could not clear a verification code: {}", ex.getClass().getSimpleName());
        }
    }

    private SimpleMailMessage buildMessage(String email, String subject, String textBody) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(StringUtils.hasText(from) ? from : username);
        message.setTo(email);

        /*
          The code is deliberately NOT in the subject line. Putting it there is
          common and does save a tap, but it also renders the code on a locked
          screen in a notification preview, where somebody else can read it.
        */
        message.setSubject(subject);

        // Both languages, as everywhere else a person is addressed directly
        message.setText(textBody);

        return message;
    }

    /*
      Sends via Brevo HTTPS API (port 443) instead of SMTP (blocked on Render free tier).
      The subject and body arrive ready-built, so both purposes ride this one
      transport. Package-private rather than private purely as a test seam: the
      delivery tests stub it instead of reaching the real Brevo endpoint.
    */
    void sendViaBrevo(String email, String subject, String textBody) {
        String senderEmail = StringUtils.hasText(from) ? from.trim()
                : (username != null ? username.trim() : "");
        String apiKey = brevoApiKey != null ? brevoApiKey.trim() : "";
        String url = StringUtils.hasText(brevoApiUrl) ? brevoApiUrl.trim()
                : "https://api.brevo.com/v3/smtp/email";

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", brevoSenderName, "email", senderEmail),
                "to", List.of(Map.of("email", email)),
                "subject", subject,
                "textContent", textBody);

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10)); // bounded like SMTP timeouts above
        factory.setReadTimeout(Duration.ofSeconds(10));

        RestClient client = RestClient.builder().requestFactory(factory).build();
        client.post()
                .uri(url)
                .header("api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }

    private static String buildTextBody(Purpose purpose, String code) {
        return switch (purpose) {
            case REGISTRATION -> registrationTextBody(code);
            case PASSWORD_RESET -> passwordResetTextBody(code);
        };
    }

    private static String registrationTextBody(String code) {
        return """
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
                """.formatted(code, code);
    }

    private static String passwordResetTextBody(String code) {
        return """
                Clean Bharat - password recovery

                You asked to reset your Clean Bharat account password because
                the current one was forgotten. Your recovery code is: %s

                Enter this code on your profile page to set a new password.
                It expires in 10 minutes and can be used only once. If you did
                not request a password reset, you can ignore this message -
                your current password keeps working and nothing has changed.

                ----------------------------------------------------------

                स्वच्छ भारत - पासवर्ड पुनर्प्राप्ति

                आपने अपना पासवर्ड भूलने के कारण Clean Bharat खाते का पासवर्ड
                रीसेट करने का अनुरोध किया था। आपका रिकवरी कोड है: %s

                नया पासवर्ड सेट करने के लिए इस कोड को अपने प्रोफ़ाइल पेज पर दर्ज
                करें। यह कोड 10 मिनट में समाप्त हो जाएगा और केवल एक बार उपयोग
                किया जा सकता है। यदि आपने पासवर्ड रीसेट का अनुरोध नहीं किया है,
                तो इस संदेश की अनदेखी करें - आपका वर्तमान पासवर्ड काम करता रहेगा।
                """.formatted(code, code);
    }

    // Root cause helpers for actionable Render logs (no addresses, codes, or keys)
    private static String rootCauseName(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }

    private static String rootCauseMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String msg = t.getMessage();
        if (!StringUtils.hasText(msg)) {
            return "n/a";
        }
        // Truncate defensively; SMTP messages can embed host strings
        return msg.length() > 200 ? msg.substring(0, 200) : msg;
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
