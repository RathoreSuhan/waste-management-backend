package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.exception.InvalidRegistrationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
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
