package com.cleanbharat.wastemanagement.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which requests the JWT filter declines to process.
 *
 * Skipped paths arrive anonymous even when a valid token is attached - the
 * posture every public leaderboard scope relies on. The personal ranking is
 * deliberately NOT skipped: it can only answer once the filter has read the
 * token, so an identity is available for SecurityConfig to authorize.
 */
class JwtAuthenticationFilterPathTest {

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(null, null);

    private boolean skipped(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        return filter.shouldNotFilter(request);
    }

    @Test
    void thePublicLeaderboardScopesStaySkipped() {
        assertTrue(skipped("/api/leaderboard"));
        assertTrue(skipped("/api/leaderboard/state/Bihar"));
        assertTrue(skipped("/api/leaderboard/district/Bihar/Gaya"));
        assertTrue(skipped("/api/leaderboard/city/Patna"));
    }

    @Test
    void thePersonalRankingIsProcessedSoAnIdentityIsAvailable() {
        assertFalse(skipped("/api/leaderboard/me"));
    }

    @Test
    void theOtherPublicSkipsAreUnchanged() {
        assertTrue(skipped("/api/auth/login"));
        assertTrue(skipped("/api/files/anything"));
        assertTrue(skipped("/api/health"));
        assertTrue(skipped("/actuator/health"));
    }

    @Test
    void protectedPathsAreStillProcessed() {
        assertFalse(skipped("/api/reports"));
        assertFalse(skipped("/api/admin/users"));
        assertFalse(skipped("/api/cleanup-assignments/pending"));
    }
}
