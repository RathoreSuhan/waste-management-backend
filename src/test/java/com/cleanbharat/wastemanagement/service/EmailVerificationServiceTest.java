package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the email verification codes.
 *
 * This class is the only thing standing between the sign-up form and an account
 * created for an address nobody owns, so what is pinned here is every way that
 * could go wrong quietly:
 *
 * The code is never stored in the clear, and the address is never stored at all.
 * A ten-minute window in a third-party Redis is still long enough for a dump to
 * matter, and these keys sit beside the rate limiter's in the same instance.
 *
 * Guesses are counted and capped. Six digits is one in a million, which is only
 * a real barrier while the number of tries is limited - uncapped, ten minutes is
 * plenty.
 *
 * It fails closed. Unlike RateLimitService, which deliberately lets traffic
 * through when Redis is unreachable, a verification that cannot be read or
 * recorded must refuse: the alternative is an account with an unproven address.
 *
 * And a code that was never delivered does not lock the person out - the
 * cooldown must not outlive a send that failed.
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "citizen@example.com";

    private static final String SENDER = "noreply@cleanbharat.tech";

    /** Pulls the code out of the message body, so no test has to know it upfront. */
    private static final Pattern SIX_DIGITS = Pattern.compile("\\b(\\d{6})\\b");

    @Mock private StringRedisTemplate redisTemplate;

    @Mock private ValueOperations<String, String> valueOperations;

    @Mock private JavaMailSender mailSender;

    private EmailVerificationService emailVerificationService;

    @BeforeEach
    void setUp() {
        emailVerificationService = new EmailVerificationService(redisTemplate, mailSender);

        // @Value fields are not populated without a Spring context
        ReflectionTestUtils.setField(emailVerificationService, "username", SENDER);
        ReflectionTestUtils.setField(emailVerificationService, "from", "");
    }

    // ---------------------------------------------------------------------
    // Sending
    // ---------------------------------------------------------------------

    @Test
    void storesTheHashOfTheCodeItEmailedAndNeverTheCodeItself() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        emailVerificationService.sendCode(EMAIL);

        // What went to Redis
        ArgumentCaptor<String> storedValue = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);

        verify(valueOperations).set(
                storedKey.capture(),
                storedValue.capture(),
                eq(Duration.ofMinutes(10))
        );

        // What went to the person
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());

        String emailedCode = extractCode(sent.getValue().getText());

        assertEquals(sha256Hex(emailedCode), storedValue.getValue(),
                "Redis must hold the hash of the code that was emailed");

        // A full SHA-256 in hex, so nothing short or reversible went in
        assertTrue(storedValue.getValue().matches("[0-9a-f]{64}"));

        // The address is fingerprinted, not recorded
        assertTrue(storedKey.getValue().startsWith("evc:"));
        assertFalse(storedKey.getValue().contains(EMAIL));
    }

    @Test
    void sendsSixDigitsFromBothLanguagesOfTheMessage() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        emailVerificationService.sendCode(EMAIL);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());

        SimpleMailMessage message = sent.getValue();

        assertEquals(SENDER, message.getFrom()); // Gmail refuses a From it did not authenticate
        assertEquals(EMAIL, message.getTo()[0]);

        // Not in the subject, where a locked screen would show it to a passer-by
        assertFalse(message.getSubject().matches(".*\\d{6}.*"));

        assertTrue(message.getText().contains("स्वच्छ भारत"), "the Hindi half must be there too");
    }

    @Test
    void refusesToSendWhenNoMailAccountIsConfigured() {

        // The state of a fresh deployment with no MAIL_USERNAME
        ReflectionTestUtils.setField(emailVerificationService, "username", "");

        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.sendCode(EMAIL));

        // Nothing is written and nothing is sent, so no account can follow
        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(mailSender);
    }

    @Test
    void refusesASecondCodeWhileTheFirstIsStillFresh() {

        /*
          560s left of a 600s life means it was issued 40 seconds ago, inside the
          60-second cooldown. Without this, the endpoint is a way to post mail to
          somebody else's inbox repeatedly.
        */
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(560L);

        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.sendCode(EMAIL));

        verifyNoInteractions(mailSender);
    }

    @Test
    void allowsANewCodeOnceTheCooldownHasPassed() {

        // 500s left means it was issued 100 seconds ago
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(500L);

        emailVerificationService.sendCode(EMAIL);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void aCodeThatCouldNotBeSentDoesNotBlockTheNextAttempt() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);
        doThrow(new MailSendException("mailbox unreachable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.sendCode(EMAIL));

        /*
          Both keys are cleared. Leaving them would hold the cooldown against a
          code that never arrived, so the person could neither use one nor ask
          for another for a full minute.
        */
        verify(redisTemplate, atLeastOnce()).delete(anyString());
    }

    @Test
    void refusesWhenTheCodeCouldNotBeRecorded() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        // Fails closed: a code nothing can check must not be treated as sent
        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.sendCode(EMAIL));

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    // ---------------------------------------------------------------------
    // Verifying
    // ---------------------------------------------------------------------

    @Test
    void acceptsTheCodeThatWasSentAndConsumesIt() {

        givenStoredCode("123456", 1L);

        emailVerificationService.verifyCode(EMAIL, "123456");

        // Removed on use, so one code can create at most one account
        verify(redisTemplate, atLeastOnce()).delete(anyString());
    }

    @Test
    void rejectsAWrongCode() {

        givenStoredCode("123456", 1L);

        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(EMAIL, "999999"));
    }

    @Test
    void countsEveryGuessWhetherOrNotItWasRight() {

        givenStoredCode("123456", 1L);

        emailVerificationService.verifyCode(EMAIL, "123456");

        /*
          Incremented before the comparison. Counting only failures would leave a
          correct guess cheaper than a wrong one, which is a signal worth having
          if you are working through a million of them.
        */
        verify(valueOperations).increment("eva:" + fingerprintOf(EMAIL));
    }

    @Test
    void refusesOnceTooManyGuessesHaveBeenSpent() {

        // Six attempts against a cap of five
        givenStoredCode("123456", 6L);

        // Refused even though this guess happens to be correct: the code is burnt
        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(EMAIL, "123456"));
    }

    @Test
    void refusesWhenNoCodeIsOutstanding() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null); // expired, or never asked for

        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(EMAIL, "123456"));
    }

    @Test
    void refusesWhenTheStoredCodeCannotBeRead() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        // Fails closed, unlike the rate limiter which deliberately fails open
        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(EMAIL, "123456"));
    }

    @Test
    void theStoredKeyIsTheSameForAnAddressTypedInAnyCase() {

        givenStoredCode("123456", 1L);

        // The form keeps what was typed; the code must still be found
        emailVerificationService.verifyCode("Citizen@Example.COM", "123456");

        verify(valueOperations).get("evc:" + fingerprintOf(EMAIL));
    }

    // ---------------------------------------------------------------------
    // Purposes: registration codes and password-reset codes never mix
    // ---------------------------------------------------------------------

    @Test
    void aPasswordResetCodeIsNamespacedApartFromRegistration() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        emailVerificationService.sendCode(EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET);

        ArgumentCaptor<String> storedKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(valueOperations).set(storedKey.capture(), anyString(), eq(Duration.ofMinutes(10)));
        verify(mailSender).send(sent.capture());

        // Its own namespace, so a reset code can never stand in for a
        // sign-up code and the two cooldowns stay independent
        assertTrue(storedKey.getValue().startsWith("prc:"), storedKey.getValue());

        // The email says what the code is for, in both languages
        assertEquals("Reset your Clean Bharat password", sent.getValue().getSubject());
        assertTrue(sent.getValue().getText().contains("password recovery"), sent.getValue().getText());
        assertTrue(sent.getValue().getText().contains("पासवर्ड पुनर्प्राप्ति"), sent.getValue().getText());
    }

    @Test
    void aResetCodeCannotAuthorizeRegistrationAndViceVersa() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        // A password-reset code is outstanding...
        emailVerificationService.sendCode(EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET);
        String resetCode = sentCodeViaSmtp();

        // ...and a registration code is outstanding alongside it
        emailVerificationService.sendCode(EMAIL);
        String registrationCode = sentCodeViaSmtp();

        // Each namespace holds exactly its own code's hash
        String fingerprint = fingerprintOf(EMAIL);
        when(valueOperations.get("prc:" + fingerprint)).thenReturn(sha256Hex(resetCode));
        when(valueOperations.get("evc:" + fingerprint)).thenReturn(sha256Hex(registrationCode));

        // A reset code cannot authorize registration...
        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(EMAIL, resetCode));

        // ...and a registration code cannot authorize a password reset
        assertThrows(InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(
                        EMAIL, registrationCode, EmailVerificationService.Purpose.PASSWORD_RESET));

        // Each code still verifies for its own purpose
        emailVerificationService.verifyCode(
                EMAIL, resetCode, EmailVerificationService.Purpose.PASSWORD_RESET);
        emailVerificationService.verifyCode(EMAIL, registrationCode);
    }

    @Test
    void aPasswordResetEmailRidesTheBrevoTransportWhenConfigured() {

        EmailVerificationService spied = Mockito.spy(new EmailVerificationService(redisTemplate, mailSender));
        ReflectionTestUtils.setField(spied, "username", SENDER);
        ReflectionTestUtils.setField(spied, "from", "");
        ReflectionTestUtils.setField(spied, "brevoApiKey", "test-key");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        doNothing().when(spied).sendViaBrevo(anyString(), anyString(), anyString());

        spied.sendCode(EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET);

        // The Brevo HTTPS path carried the reset email, with the recovery
        // subject and body - no real request leaves the test
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(spied).sendViaBrevo(eq(EMAIL), subject.capture(), body.capture());

        assertEquals("Reset your Clean Bharat password", subject.getValue());
        assertTrue(body.getValue().contains("recovery code"), body.getValue());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void registrationDeliveryIsUnchangedOnTheBrevoPath() {

        EmailVerificationService spied = Mockito.spy(new EmailVerificationService(redisTemplate, mailSender));
        ReflectionTestUtils.setField(spied, "username", SENDER);
        ReflectionTestUtils.setField(spied, "from", "");
        ReflectionTestUtils.setField(spied, "brevoApiKey", "test-key");

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        doNothing().when(spied).sendViaBrevo(anyString(), anyString(), anyString());

        spied.sendCode(EMAIL);

        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        verify(spied).sendViaBrevo(eq(EMAIL), subject.capture(), anyString());

        assertEquals("Verify your Clean Bharat email address", subject.getValue());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void theSmtpPathServesTheResetEmailWhenBrevoIsNotConfigured() {

        // brevoApiKey stays unset (setUp), so the reset goes out via SMTP
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.getExpire(anyString(), eq(TimeUnit.SECONDS))).thenReturn(null);

        emailVerificationService.sendCode(EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("Reset your Clean Bharat password", sent.getValue().getSubject());
    }

    /** Captures the code out of the SMTP message that a send just produced. */
    private String sentCodeViaSmtp() {
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, Mockito.atLeastOnce()).send(sent.capture());
        List<SimpleMailMessage> messages = sent.getAllValues();
        return extractCode(messages.get(messages.size() - 1).getText());
    }

    @Test
    void recoveryCooldownsAndAttemptCountersAreScopedByAccountContext() {

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        String fingerprint = fingerprintOf(EMAIL);

        // A live cooldown on the municipal context's code key (issued ~30s
        // ago: 570 seconds of its 600-second TTL remain)
        when(redisTemplate.getExpire("prc:municipal:" + fingerprint, TimeUnit.SECONDS))
                .thenReturn(570L);

        InvalidRegistrationException cooledDown = assertThrows(
                InvalidRegistrationException.class,
                () -> emailVerificationService.sendCode(
                        EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET, "municipal"));
        assertTrue(cooledDown.getMessage().contains("Please wait"), cooledDown.getMessage());

        // ...leaves the user context free to send its own code
        when(redisTemplate.getExpire("prc:user:" + fingerprint, TimeUnit.SECONDS)).thenReturn(null);
        emailVerificationService.sendCode(EMAIL, EmailVerificationService.Purpose.PASSWORD_RESET, "user");

        verify(valueOperations).set(
                eq("prc:user:" + fingerprint), anyString(), eq(Duration.ofMinutes(10)));

        // Attempt counters are equally separate: five burnt guesses against
        // the user context refuse the user context but not the municipal one
        when(valueOperations.get("prc:user:" + fingerprint)).thenReturn(sha256Hex("654321"));
        when(valueOperations.get("prc:municipal:" + fingerprint)).thenReturn(sha256Hex("654321"));
        when(valueOperations.increment("pra:user:" + fingerprint)).thenReturn(6L);
        when(valueOperations.increment("pra:municipal:" + fingerprint)).thenReturn(1L);

        InvalidRegistrationException burntOut = assertThrows(
                InvalidRegistrationException.class,
                () -> emailVerificationService.verifyCode(
                        EMAIL, "654321", EmailVerificationService.Purpose.PASSWORD_RESET, "user"));
        assertTrue(burntOut.getMessage().contains("Too many incorrect"), burntOut.getMessage());

        emailVerificationService.verifyCode(
                EMAIL, "654321", EmailVerificationService.Purpose.PASSWORD_RESET, "municipal");
    }

    // ---------------------------------------------------------------------
    // Test fixtures
    // ---------------------------------------------------------------------

    /** A code already outstanding for EMAIL, with the given number of guesses spent. */
    private void givenStoredCode(String code, Long attempts) {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(sha256Hex(code));
        when(valueOperations.increment(anyString())).thenReturn(attempts);
    }

    private static String extractCode(String messageText) {
        Matcher matcher = SIX_DIGITS.matcher(messageText);

        assertTrue(matcher.find(), "the message must carry a six-digit code");

        // No assertion on the value: SecureRandom may legitimately produce any
        // of the million, 000000 included, and pinning one would be flaky
        return matcher.group(1);
    }

    /** Mirrors the production hash, which pins the algorithm as well as the behaviour. */
    private static String sha256Hex(String value) {
        return toHex(digest(value), Integer.MAX_VALUE);
    }

    /** Mirrors the production fingerprint: half a SHA-256 of the lower-cased address. */
    private static String fingerprintOf(String email) {
        return toHex(digest(email.toLowerCase()), 16);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
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
