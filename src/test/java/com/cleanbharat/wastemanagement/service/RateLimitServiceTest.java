package com.cleanbharat.wastemanagement.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Redis counting half of the rate limiter.
 *
 * What is pinned here is everything that would fail silently in production:
 *
 * The key shape, because a key that drifted out of the "rl:" namespace could
 * collide with a RedisCacheManager entry and quietly poison a cached value; and
 * because the authenticated identity is an email address that must never be
 * written to Redis in the clear.
 *
 * The window argument, because the bucket key rotates on absolute clock
 * boundaries: an expiry longer than the time left in the current window would
 * outlive its own bucket and quote a Retry-After that is too long, while an
 * expiry refreshed on every hit rather than only the first would turn a
 * one-minute limit into a permanent lockout - both without raising an error.
 *
 * And the fail-open behavior, because Redis Cloud is on a free plan: if a blip
 * there started rejecting API traffic, the limiter would be a bigger outage than
 * the abuse it exists to stop.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"rawtypes", "unchecked"})
class RateLimitServiceTest {

    private static final String IDENTITY = "user:citizen@example.com";

    private static final int LIMIT = 5;

    private static final int WINDOW_SECONDS = 60;

    @Mock private StringRedisTemplate redisTemplate;

    @InjectMocks private RateLimitService rateLimitService;

    @Captor private ArgumentCaptor<List<String>> keysCaptor;

    @Captor private ArgumentCaptor<String> scriptArgumentCaptor;

    // ---------------------------------------------------------------------
    // Allow / reject decision
    // ---------------------------------------------------------------------

    @Test
    void allowsRequestWhileCountIsWithinTheLimit() {

        givenRedisReturns(5L, 30L); // exactly at the limit is still allowed

        long retryAfter = rateLimitService.registerRequest(
                "auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        assertEquals(0, retryAfter);
    }

    @Test
    void rejectsWithRemainingTtlOnceTheLimitIsExceeded() {

        givenRedisReturns(6L, 42L);

        long retryAfter = rateLimitService.registerRequest(
                "auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        // The caller waits only until this window resets, not a full window
        assertEquals(42, retryAfter);
    }

    @Test
    void fallsBackToTheRestOfTheWindowWhenRedisReportsNoTtl() {

        // -1 means "key exists without an expiry", -2 means "already gone".
        // Neither is a usable Retry-After, so the caller is told to wait exactly
        // as long as the expiry that was just requested - never a full window
        // more, which would be wrong for any bucket opened mid-window.
        givenRedisReturns(9L, -1L);

        long retryAfter = rateLimitService.registerRequest(
                "auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        assertEquals(Long.parseLong(capturedScriptArgument()), retryAfter);
    }

    // ---------------------------------------------------------------------
    // Key shape - the part that must never collide with the cache namespace
    // ---------------------------------------------------------------------

    @Test
    void keyUsesTheDedicatedPrefixScopeAndCurrentWindow() {

        givenRedisReturns(1L, 60L);

        rateLimitService.registerRequest("ai", IDENTITY, LIMIT, WINDOW_SECONDS);

        String key = capturedKey();
        String[] segments = key.split(":");

        assertTrue(key.startsWith("rl:"), "must stay inside the rate-limit namespace");
        assertFalse(key.contains("::"), "must not look like a RedisCacheManager key");

        assertEquals(4, segments.length);
        assertEquals("rl", segments[0]);
        assertEquals("ai", segments[1]);

        long expectedWindow = Instant.now().getEpochSecond() / WINDOW_SECONDS;
        assertEquals(expectedWindow, Long.parseLong(segments[3]));
    }

    @Test
    void keyHashesTheIdentityInsteadOfStoringTheEmailAddress() {

        givenRedisReturns(1L, 60L);

        rateLimitService.registerRequest("auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        String fingerprint = capturedKey().split(":")[2];

        assertFalse(capturedKey().contains("citizen@example.com"));
        assertEquals(16, fingerprint.length());
        assertTrue(fingerprint.matches("[0-9a-f]{16}"));
    }

    @Test
    void differentIdentitiesCountIntoDifferentBuckets() {

        givenRedisReturns(1L, 60L);

        rateLimitService.registerRequest("auth", "user:one@example.com", LIMIT, WINDOW_SECONDS);
        rateLimitService.registerRequest("auth", "user:two@example.com", LIMIT, WINDOW_SECONDS);

        List<String> keys = capturedKeys();
        assertNotEquals(keys.get(0), keys.get(1));
    }

    @Test
    void differentScopesCountIntoDifferentBucketsForTheSameCaller() {

        givenRedisReturns(1L, 60L);

        rateLimitService.registerRequest("ai", IDENTITY, LIMIT, WINDOW_SECONDS);
        rateLimitService.registerRequest("community", IDENTITY, LIMIT, WINDOW_SECONDS);

        List<String> keys = capturedKeys();
        assertNotEquals(keys.get(0), keys.get(1));
    }

    @Test
    void oneAccountCannotEarnASecondBucketByChangingTheCaseOfItsEmail() {

        givenRedisReturns(1L, 60L);

        rateLimitService.registerRequest("auth", "user:Citizen@Example.com", LIMIT, WINDOW_SECONDS);
        rateLimitService.registerRequest("auth", "user:citizen@example.com", LIMIT, WINDOW_SECONDS);

        List<String> keys = capturedKeys();
        assertEquals(keys.get(0), keys.get(1));
    }

    // ---------------------------------------------------------------------
    // Expiry
    // ---------------------------------------------------------------------

    @Test
    void expiresTheBucketWhenItsWindowEndsNotAFullWindowLater() {

        givenRedisReturns(1L, 90L);

        long before = Instant.now().getEpochSecond();
        rateLimitService.registerRequest("pub", IDENTITY, LIMIT, 90);
        long after = Instant.now().getEpochSecond();

        long expiry = Long.parseLong(capturedScriptArgument());
        long windowIndex = Long.parseLong(capturedKey().split(":")[3]);

        /*
          The key rotates at absolute multiples of the window, so the bucket dies
          at (windowIndex + 1) * 90 regardless of when it was opened. Expiring at
          exactly that instant means "expiry + now == the next boundary", which
          holds no matter which second the call landed on - so this pins the fix
          without freezing the clock.
        */
        long impliedNow = (windowIndex + 1) * 90 - expiry;

        assertTrue(
                impliedNow >= before && impliedNow <= after,
                "expiry must run out at the window boundary, not " + expiry + "s from now"
        );

        // Never 0 (EXPIRE would delete the key outright) and never past the boundary
        assertTrue(expiry >= 1 && expiry <= 90, "expiry out of range: " + expiry);
    }

    // ---------------------------------------------------------------------
    // Availability
    // ---------------------------------------------------------------------

    @Test
    void failsOpenWhenRedisIsUnreachable() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis Cloud unreachable"));

        long retryAfter = rateLimitService.registerRequest(
                "auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        assertEquals(0, retryAfter, "a Redis outage must not take the API down with it");
    }

    @Test
    void failsOpenWhenTheScriptReplyIsNotUsable() {

        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(null);

        long retryAfter = rateLimitService.registerRequest(
                "auth", IDENTITY, LIMIT, WINDOW_SECONDS);

        assertEquals(0, retryAfter);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void givenRedisReturns(long hits, long secondsUntilReset) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(List.of(hits, secondsUntilReset));
    }

    private String capturedKey() {
        return capturedKeys().get(0);
    }

    private List<String> capturedKeys() {
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce())
                .execute(any(RedisScript.class), keysCaptor.capture(), anyString());

        return keysCaptor.getAllValues().stream().map(List::getFirst).toList();
    }

    /** ARGV[1] - the expiry the service asked Redis to put on the bucket. */
    private String capturedScriptArgument() {
        verify(redisTemplate, org.mockito.Mockito.atLeastOnce())
                .execute(any(RedisScript.class), anyList(), scriptArgumentCaptor.capture());

        return scriptArgumentCaptor.getValue();
    }
}
