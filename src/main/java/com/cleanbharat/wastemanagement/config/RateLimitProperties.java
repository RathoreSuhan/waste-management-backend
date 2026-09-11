package com.cleanbharat.wastemanagement.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * ============================================================
 *  Rate Limit Policy for Clean Bharat Backend
 * ============================================================
 *
 *  Every rate-limit decision in the application is made here.
 *
 *  WHY ONE CLASS
 *  -------------
 *  A limit scattered across eighteen controllers is a limit nobody can audit.
 *  Keeping the whole policy in one file means the answer to "what is throttled,
 *  and how hard?" is a single screen of code, and adding a route to a tier is a
 *  one-line change that cannot accidentally miss a sibling endpoint.
 *
 *  THE TIERS
 *  ---------
 *  auth      Credential paths. These are the ones an attacker sprays passwords
 *            at, and JwtAuthenticationFilter.shouldNotFilter deliberately skips
 *            them, so they are always keyed by IP - there is no principal yet.
 *
 *  ai        Anything that reaches Google Gemini or Cloudinary before a business
 *            rule can reject it. Filing a report runs AI validation, duplicate
 *            detection and a Cloudinary upload; /api/files/upload is anonymous
 *            and uploads straight to Cloudinary. Rejected at this tier, the paid
 *            call is never made at all - which is the whole point of putting the
 *            limiter in the filter chain rather than in the service layer.
 *
 *  community Votes, comments and public-feed engagement. Cheap individually,
 *            but they write rows and skew analytics when scripted.
 *
 *  staff     Admin and municipal consoles. Real people clicking through queues,
 *            so the ceiling only has to stop a runaway script.
 *
 *  pub       The fall-through, which in practice is the public read surface -
 *            reports, public feed, leaderboard, platform-impact analytics.
 *            Generous, because these are exactly the pages a visitor browses.
 *
 *  WHAT IS DELIBERATELY EXEMPT
 *  ---------------------------
 *  /api/health is the cold-start ping. The frontend polls it every 1.5s for up
 *  to two minutes while the free-plan container starts, and a scheduled workflow
 *  pings it too. Throttling it would break the warm-up flow the deployment is
 *  built around, and it costs nothing to serve (see HealthController).
 *
 *  TUNING
 *  ------
 *  Every number below has an in-code default, so the application runs unchanged
 *  with no new configuration. Any of them can be overridden per environment:
 *
 *      ratelimit.enabled=true
 *      ratelimit.window-seconds=60
 *      ratelimit.public-per-minute=100
 *      ratelimit.auth-per-minute=10
 *      ratelimit.expensive-per-minute=5
 *      ratelimit.community-per-minute=30
 *      ratelimit.staff-per-minute=60
 *      ratelimit.trusted-proxy-hops=1
 * ============================================================
 */
@Getter
@Configuration
public class RateLimitProperties {

    /* ---------------- Scope names, used as the Redis key segment ---------------- */

    static final String SCOPE_AUTH = "auth";

    static final String SCOPE_AI = "ai";

    static final String SCOPE_COMMUNITY = "community";

    static final String SCOPE_STAFF = "staff";

    static final String SCOPE_PUBLIC = "pub";

    /**
     * Master switch. Turning this off skips the filter entirely, which is the
     * escape hatch if a limit ever turns out to be wrong in production.
     */
    @Value("${ratelimit.enabled:true}")
    private boolean enabled;

    /** Length of one counting window. Every tier below is "per this window". */
    @Value("${ratelimit.window-seconds:60}")
    private int windowSeconds;

    /** Public read surface - far above what any human reader produces. */
    @Value("${ratelimit.public-per-minute:100}")
    private int publicPerMinute;

    /** Login, registration, password change. Stops spraying, not typos. */
    @Value("${ratelimit.auth-per-minute:10}")
    private int authPerMinute;

    /** Gemini and Cloudinary paths. The money tier. */
    @Value("${ratelimit.expensive-per-minute:5}")
    private int expensivePerMinute;

    /** Votes, comments, public-feed engagement. */
    @Value("${ratelimit.community-per-minute:30}")
    private int communityPerMinute;

    /** Admin and municipal consoles. */
    @Value("${ratelimit.staff-per-minute:60}")
    private int staffPerMinute;

    /**
     * How many proxy hops in front of this application are trusted.
     *
     * The service runs behind Render's proxy, so getRemoteAddr() is that proxy
     * and would put every anonymous visitor in one shared bucket. The proxy
     * APPENDS the peer it actually observed to X-Forwarded-For, so entries are
     * counted from the right: the rightmost was written by infrastructure we
     * trust, anything further left was supplied by the caller and can say
     * whatever it likes.
     *
     * Set to 0 to ignore the header completely (direct exposure, or local runs).
     * Raise it to 2 if another trusted proxy is ever placed in front.
     */
    @Value("${ratelimit.trusted-proxy-hops:1}")
    private int trustedProxyHops;

    /**
     * One tier: which Redis key segment to count under, and how many requests
     * that segment allows per window.
     */
    public record Rule(String scope, int limit) {
    }

    /**
     * Map one request to its tier.
     *
     * @return the rule to enforce, or null when the request is not rate limited
     */
    public Rule resolve(String method, String path) {

        // Cold-start ping and container probes - see the class comment
        if (path.equals("/api/health") || path.startsWith("/actuator")) {
            return null;
        }

        /*
          Preflight carries no credentials and is answered by CorsFilter before
          it ever reaches here. Skipped anyway so a browser can never be told to
          slow down on a request the user did not make.
        */
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return null;
        }

        boolean post = "POST".equalsIgnoreCase(method);

        // Credential paths
        if (path.startsWith("/api/auth/") || path.equals("/api/account/password")) {
            return new Rule(SCOPE_AUTH, authPerMinute);
        }

        // Everything that spends Gemini quota or Cloudinary storage
        if ((post && path.equals("/api/reports"))
                || (post && path.equals("/api/files/upload"))
                || (post && path.startsWith("/api/cleanup-assignments/")
                        && path.endsWith("/upload-image"))
                || (post && path.startsWith("/api/cleanup-activity-logs/"))
                || ((post || "PUT".equalsIgnoreCase(method))
                        && path.startsWith("/api/cleanup-proposals/"))
                || path.startsWith("/test")) {

            return new Rule(SCOPE_AI, expensivePerMinute);
        }

        // Community writes. Reads of the same resources fall through to pub.
        if (!"GET".equalsIgnoreCase(method)
                && (path.startsWith("/api/votes")
                        || path.startsWith("/api/comments/")
                        || path.startsWith("/api/public-feed/"))) {

            return new Rule(SCOPE_COMMUNITY, communityPerMinute);
        }

        // Staff consoles, read and write alike
        if (path.startsWith("/api/admin/")
                || path.startsWith("/api/municipal-corporations")
                || path.startsWith("/api/cleanup-approvals/")) {

            return new Rule(SCOPE_STAFF, staffPerMinute);
        }

        // Fall-through: the public read surface
        return new Rule(SCOPE_PUBLIC, publicPerMinute);
    }
}
