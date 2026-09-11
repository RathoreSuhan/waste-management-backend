package com.cleanbharat.wastemanagement.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * ============================================================
 *  Redis-Backed Fixed Window Rate Limiter
 * ============================================================
 *
 *  Counts requests per caller per window in Redis, so every application
 *  instance shares one budget. The policy (which routes, which numbers) lives
 *  in RateLimitProperties; this class only knows how to count.
 *
 *  It reuses the autoconfigured StringRedisTemplate, which sits on the same
 *  RedisConnectionFactory as the existing RedisCacheManager - one connection to
 *  Redis Cloud, not two.
 *
 *  KEY SHAPE
 *  ---------
 *      rl:{scope}:{fingerprint}:{window}
 *
 *  The "rl:" prefix keeps these clear of the cache manager's "cacheName::key"
 *  namespace, so nothing here can ever collide with or evict a cached value.
 *
 *  The window index is floor(epochSeconds / windowSeconds), so the key itself
 *  rotates every window. Old buckets are never revisited and expire on their
 *  own - there is no cleanup job and no unbounded growth.
 * ============================================================
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /** Dedicated namespace. Nothing else in the application writes "rl:" keys. */
    static final String KEY_PREFIX = "rl:";

    /** Half a SHA-256 is 64 bits of identity - collision-free at this scale, and short. */
    private static final int FINGERPRINT_LENGTH = 16;

    /**
     * INCR and EXPIRE as one atomic step.
     *
     * Doing them as two commands leaves a window where the process can die after
     * INCR but before EXPIRE, stranding a key with no TTL that blocks that caller
     * forever. Setting the TTL only on the first hit is what makes this a fixed
     * window: later hits inside the same window must not push the expiry out.
     *
     * ARGV[1] is the seconds left in the CURRENT window, not the window length -
     * see registerRequest. The TTL comes back in the same reply, so the exact
     * Retry-After costs no extra roundtrip.
     */
    private static final RedisScript<List> COUNT_SCRIPT = new DefaultRedisScript<>(
            """
            local hits = redis.call('INCR', KEYS[1])
            if hits == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return { hits, redis.call('TTL', KEYS[1]) }
            """,
            List.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * Record one request and decide whether it is allowed.
     *
     * @param identity      the caller, already resolved from the server request
     * @return 0 when the request is within its allowance, otherwise the number of
     *         seconds until the current window resets
     */
    @SuppressWarnings("unchecked")
    public long registerRequest(
            String scope,
            String identity,
            int limit,
            int windowSeconds
    ) {

        /*
          One "now" for both the key and the expiry.

          Reading the clock twice could put the bucket index in one window and
          the TTL in the next, so a caller could be counted into a window that
          expires almost immediately.
        */
        long now = Instant.now().getEpochSecond();

        /*
          Seconds left in THIS window, not the window length.

          The key rotates at absolute multiples of windowSeconds, so a bucket
          first touched at :55 of a 60s window resets 5 seconds later, not 60.
          Sending the full window would set a TTL that outlives its own bucket
          and quote a Retry-After up to a whole window too long. Always in
          [1, windowSeconds], so EXPIRE can never receive 0.
        */
        long remaining = windowSeconds - Math.floorMod(now, windowSeconds);

        String key = buildKey(scope, identity, windowSeconds, now);

        try {
            List<Long> result = redisTemplate.execute(
                    COUNT_SCRIPT,
                    List.of(key),
                    String.valueOf(remaining)
            );

            if (result == null || result.size() < 2) {
                return 0; // unexpected reply shape - allow rather than block
            }

            long hits = result.get(0);
            long secondsUntilReset = result.get(1);

            if (hits <= limit) {
                return 0;
            }

            // TTL can read -1/-2 if the key vanished between the two calls above
            return secondsUntilReset > 0 ? secondsUntilReset : remaining;

        } catch (DataAccessException ex) {
            /*
              Fail open. Redis Cloud is on a free plan and can blink; a limiter
              that takes the whole API down with it is worse than no limiter.
            */
            log.warn(
                    "Rate limit check skipped, Redis unavailable: {}",
                    ex.getClass().getSimpleName()
            );
            return 0;
        }
    }

    /** Package-private so the test can assert the exact key shape. */
    String buildKey(String scope, String identity, int windowSeconds) {
        return buildKey(scope, identity, windowSeconds, Instant.now().getEpochSecond());
    }

    /** Overload that reuses a clock reading the caller has already taken. */
    private String buildKey(String scope, String identity, int windowSeconds, long epochSeconds) {

        long window = Math.floorDiv(epochSeconds, windowSeconds);

        return KEY_PREFIX + scope + ":" + fingerprint(identity) + ":" + window;
    }

    /**
     * The authenticated identity is an email address, so it is hashed before it
     * reaches Redis - the key still identifies one caller deterministically
     * without storing anyone's address. Lower-cased first because accounts are
     * looked up case-insensitively, and one account must not get two buckets.
     */
    private static String fingerprint(String identity) {

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(FINGERPRINT_LENGTH);

            for (int i = 0; i < FINGERPRINT_LENGTH / 2; i++) {
                hex.append(String.format("%02x", digest[i]));
            }

            return hex.toString();

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required on every JVM", ex);
        }
    }
}
