package com.cleanbharat.wastemanagement.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * ============================================================
 *  Redis Cache Configuration for Clean Bharat Backend
 * ============================================================
 *
 *  WHAT IS SPRING CACHE ABSTRACTION?
 *  -----------------------------------
 *  Spring Cache Abstraction is a declarative caching layer that sits between
 *  your service methods and the database.
 *
 *  Instead of manually writing:
 *      String cached = redis.get("key");
 *      if (cached == null) {
 *          result = database.expensiveQuery();
 *          redis.set("key", result, ttl);
 *          return result;
 *      }
 *      return deserialize(cached);
 *
 *  You simply annotate a method with @Cacheable and Spring does all that
 *  for you — automatically, behind the scenes via AOP (proxy-based).
 *
 *  HOW @Cacheable WORKS BEHIND THE SCENES:
 *  -----------------------------------------
 *  1. Spring wraps your @Service bean in a dynamic proxy at startup.
 *  2. When the method is called, the proxy intercepts the call BEFORE
 *     the method body executes.
 *  3. The proxy computes the cache key (from SpEL expression or method args).
 *  4. It checks Redis: does a value exist for this key?
 *
 *     → CACHE HIT:  Redis has the value → return it immediately.
 *                   No database query. Response time: ~2ms.
 *
 *     → CACHE MISS: Redis has no value → let the real method execute,
 *                   fetch from PostgreSQL Neon, store the result in Redis
 *                   with the configured TTL, and return it.
 *                   Response time: ~250ms (normal DB roundtrip).
 *
 *  5. On the NEXT call after a MISS, Redis HIT kicks in for all callers
 *     until the TTL expires or someone evicts the entry.
 *
 *  WHY REDIS INSTEAD OF DIRECTLY HITTING THE DATABASE?
 *  -----------------------------------------------------
 *  Our backend (Render, Singapore) calls Neon (PostgreSQL, Singapore).
 *  Each database call involves:
 *    - Network roundtrip: ~5-20ms
 *    - Query parsing & planning: ~5-10ms
 *    - Actual query execution (COUNT, SUM, AVG, JOIN): ~100-300ms
 *    - Result serialization & transfer: ~5-10ms
 *  Total: ~150-350ms per request for heavy aggregation queries.
 *
 *  Redis is in-memory and collocated:
 *    - Network roundtrip: ~1-2ms
 *    - Key lookup (O(1) hash): <0.1ms
 *    - Serialized JSON transfer: ~0.5ms
 *  Total: ~2-4ms per cached request — 50-100x faster.
 *
 *  WHAT IS TTL?
 *  ------------
 *  TTL (Time-To-Live) is the maximum age a cached entry is allowed to live
 *  in Redis before it is automatically deleted.
 *
 *  Without TTL: Stale data lives forever until the process crashes.
 *  Example: new report created → cache still says 0 reports → users see 0
 *  forever until you manually flush Redis.
 *
 *  With TTL: Even if @CacheEvict misses a case, stale data self-destructs.
 *  Example: cache TTL = 10 minutes → worst case, users see 10-minute-old data.
 *  This is an acceptable trade-off for read-heavy, low-write public data.
 *
 *  ENVIRONMENT CONFIGURATION:
 *  --------------------------
 *  Connection credentials are NOT in application.properties (security).
 *  Set this ONE environment variable on Render:
 *
 *      SPRING_DATA_REDIS_URL = redis://default:<password>@<host>:<port>
 *                                 or
 *      SPRING_DATA_REDIS_URL = rediss://default:<password>@<host>:<port>  (TLS)
 *
 *  Spring Boot autoconfigures the Lettuce connection factory from this URL.
 *  No other configuration required.
 *
 *  MEMORY BUDGET (30 MB Free Tier):
 *  ----------------------------------
 *  Cache                      | Keys | Est. Size | TTL
 *  ---------------------------|------|-----------|------
 *  homepage_impact_stats      |  1   |  ~2 KB    | 10m
 *  leaderboard_top (national) |  1   |  ~10 KB   |  5m
 *  leaderboard_top (state)    | ~10  |  ~10 KB   |  5m
 *  leaderboard_top (city)     | ~20  |  ~10 KB   |  5m
 *  ---------------------------|------|-----------|------
 *  TOTAL ESTIMATED:           |      |  ~310 KB  |
 *
 *  We use well under 1 MB of the 30 MB Redis free tier.
 * ============================================================
 */
@Configuration
@EnableCaching  // Activates Spring Cache Abstraction — scans for @Cacheable, @CacheEvict, @CachePut
public class RedisConfig {

    /**
     * Builds the Jackson JSON serializer used for every Redis cache value.
     *
     * A custom ObjectMapper is required (the application-wide one cannot be
     * reused) because Redis needs polymorphic type info embedded in the JSON
     * so it knows which class to reconstruct on cache read, e.g.
     *   {"@class":"com.cleanbharat...DashboardAnalyticsResponse","totalReports":42}
     *
     * JavaTimeModule: handles LocalDateTime / LocalDate fields - without it
     * they serialize as numeric arrays and deserialization crashes.
     *
     * activateDefaultTyping: embeds the "@class" field for safe polymorphic
     * deserialization. NON_FINAL skips final types (String, enums, etc.).
     *
     * Package-private + static so tests can round-trip cached DTOs through
     * the exact same serialization path used in production.
     */
    static GenericJackson2JsonRedisSerializer cacheValueSerializer() {

        ObjectMapper redisObjectMapper = new ObjectMapper();

        // Register JavaTimeModule so LocalDateTime fields serialize/deserialize cleanly
        redisObjectMapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings ("2025-01-15T10:30:00") not as [2025,1,15,...] arrays
        redisObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Embed Java class name in JSON for safe polymorphic deserialization
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(redisObjectMapper);
    }

    /**
     * Builds the primary CacheManager used by all @Cacheable/@CacheEvict annotations.
     *
     * Spring Boot auto-creates a basic RedisCacheManager, but we customize it to:
     *   1. Use JSON serialization (human-readable, version-safe, Jackson-based).
     *   2. Set different TTLs per cache (fine-grained expiry control).
     *   3. Never cache null values (saves memory; a miss on a null just hits DB again).
     *   4. Prefix keys with the cache name (avoids collisions between caches in Redis).
     *
     * @param connectionFactory Auto-configured by Spring Boot from SPRING_DATA_REDIS_URL
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        /* ------------------------------------------------------------------
         * Step 1: Create the JSON serializer used for all cache values.
         *
         * GenericJackson2JsonRedisSerializer stores values as JSON strings
         * in Redis - readable via redis-cli, redis-insight, or any Redis GUI.
         * (JdkSerializationRedisSerializer is NOT used: unreadable, tied to
         * the exact JVM version, breaks on refactors.)
         * ------------------------------------------------------------------ */
        GenericJackson2JsonRedisSerializer jsonSerializer = cacheValueSerializer();

        /* ------------------------------------------------------------------
         * Step 3: Define the DEFAULT cache configuration.
         *
         * This applies to all @Cacheable caches unless overridden below.
         *
         * Key points:
         *   - entryTtl(10 min): Cached entries auto-expire after 10 minutes.
         *     This is the safety net — even if @CacheEvict is missed, stale
         *     data will eventually disappear.
         *   - disableCachingNullValues(): If a method returns null, do NOT
         *     cache it. Null caching wastes memory and hides real errors.
         *   - usePrefix(true): Redis key becomes "cacheName::spELkey"
         *     e.g. "homepage_impact_stats::metrics" — prevents accidental
         *     key collisions between different caches storing the same key.
         *   - StringRedisSerializer for keys: Cache keys are plain strings
         *     (e.g. "national", "state:Maharashtra"), not serialized objects.
         * ------------------------------------------------------------------ */
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration
                .defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))  // Default TTL: 10 minutes
                .disableCachingNullValues()         // Never cache null returns
                .serializeKeysWith(
                        // Keys stored as plain UTF-8 strings
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        // Values stored as Jackson JSON
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer)
                );

        /* ------------------------------------------------------------------
         * Step 4: Define per-cache TTL overrides.
         *
         * Not all data ages at the same rate:
         *
         *   homepage_impact_stats:
         *     Changes when: new report created, or cleanup completed.
         *     @CacheEvict handles immediate invalidation.
         *     TTL (10 min) = safety net for any edge cases.
         *
         *   leaderboard_top:
         *     Changes when: reward points are awarded to cleaners.
         *     High read frequency (homepage + leaderboard page).
         *     Shorter TTL (5 min) = rank changes visible faster.
         *
         *   public_recent_cleanups: NOT cached (intentionally removed).
         *     The public feed is viewer-specific (likedByMe per signed-in
         *     user) and dominated by Cloudinary image URLs, so it is
         *     always served fresh from PostgreSQL.
         * ------------------------------------------------------------------ */
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(
                "homepage_impact_stats",        // Cache name (matches @Cacheable value=)
                defaultConfig.entryTtl(Duration.ofMinutes(10))  // 10 min TTL
        );

        cacheConfigurations.put(
                "leaderboard_top",              // National, state, city leaderboards
                defaultConfig.entryTtl(Duration.ofMinutes(5))   // 5 min TTL
        );

        /* ------------------------------------------------------------------
         * Step 5: Build and return the RedisCacheManager.
         *
         * This is the CacheManager Spring will use for all @Cacheable,
         * @CacheEvict, and @CachePut annotations across the application.
         *
         * withCacheManager() registers the per-cache TTL overrides.
         * cacheDefaults() applies to any @Cacheable cache not listed above.
         * ------------------------------------------------------------------ */
        return RedisCacheManager
                .builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
