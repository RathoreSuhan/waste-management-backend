package com.cleanbharat.wastemanagement.security;

import com.cleanbharat.wastemanagement.config.RateLimitProperties;
import com.cleanbharat.wastemanagement.service.RateLimitService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the filter that decides WHO is being counted and WHICH tier
 * applies.
 *
 * The identity rules are the security-critical part. If an authenticated caller
 * were counted by IP, one shared office or mobile-carrier address would throttle
 * everyone behind it; if identity came from anything the caller can set - a
 * parameter, a body field, a header they choose - the limit would be bypassable
 * by varying that value, which is the same as having no limit at all.
 *
 * The proxy rule matters for the same reason: the deployment sits behind a
 * proxy that appends the peer it observed to X-Forwarded-For, so only the
 * rightmost entry is trustworthy. Reading the leftmost one - the usual mistake -
 * would let a caller pick their own bucket.
 *
 * Tier resolution is pinned per method and path because a route drifting into
 * the wrong tier is invisible until either a real user is throttled or an
 * expensive Gemini/Cloudinary path stops being protected. It is pinned on the
 * DECODED path too: the container hands the filter the raw target, so a route
 * matched raw can be reached with percent-encoding while missing its own tier.
 */
@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private static final String PROXY_ADDRESS = "10.0.0.7";

    private static final String REAL_CLIENT_ADDRESS = "203.0.113.9";

    @Mock private RateLimitService rateLimitService;

    private RateLimitProperties rateLimitProperties;

    private RateLimitFilter rateLimitFilter;

    private MockHttpServletResponse response;

    private MockFilterChain filterChain;

    @BeforeEach
    void setUp() {
        // @Value fields are not populated without a Spring context
        rateLimitProperties = new RateLimitProperties();
        ReflectionTestUtils.setField(rateLimitProperties, "enabled", true);
        ReflectionTestUtils.setField(rateLimitProperties, "windowSeconds", 60);
        ReflectionTestUtils.setField(rateLimitProperties, "publicPerMinute", 100);
        ReflectionTestUtils.setField(rateLimitProperties, "authPerMinute", 10);
        ReflectionTestUtils.setField(rateLimitProperties, "expensivePerMinute", 5);
        ReflectionTestUtils.setField(rateLimitProperties, "communityPerMinute", 30);
        ReflectionTestUtils.setField(rateLimitProperties, "staffPerMinute", 60);
        ReflectionTestUtils.setField(rateLimitProperties, "trustedProxyHops", 1);

        rateLimitFilter = new RateLimitFilter(rateLimitProperties, rateLimitService);

        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();

        lenient().when(rateLimitService.registerRequest(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(0L);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------------
    // Identity
    // ---------------------------------------------------------------------

    @Test
    void anonymousRequestsAreKeyedByClientIp() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void authenticatedRequestsAreKeyedByThePrincipal() throws Exception {

        signIn("cleaner@example.com", "ROLE_CLEANER");

        MockHttpServletRequest request = request("GET", "/api/cleanup-assignments/my-tasks");
        request.setRemoteAddr(REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("user:cleaner@example.com", capturedIdentity());
    }

    @Test
    void anonymousTokensStillFallBackToIpSoVisitorsDoNotShareOneBucket() throws Exception {

        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        ));

        MockHttpServletRequest request = request("GET", "/api/reports");
        request.setRemoteAddr(REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void clientSuppliedIdentityIsIgnored() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(REAL_CLIENT_ADDRESS);

        // Everything an attacker could vary per request to escape their bucket
        request.setParameter("userId", "victim@example.com");
        request.addHeader("X-User-Id", "victim@example.com");
        request.addHeader("X-Real-IP", "198.51.100.1");

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void differentPrincipalsDoNotShareABucket() throws Exception {

        signIn("one@example.com", "ROLE_CITIZEN");
        rateLimitFilter.doFilter(request("POST", "/api/votes"), response, filterChain);

        SecurityContextHolder.clearContext();
        signIn("two@example.com", "ROLE_CITIZEN");
        rateLimitFilter.doFilter(request("POST", "/api/votes"), response, new MockFilterChain());

        List<String> identities = capturedIdentities();
        assertNotEquals(identities.get(0), identities.get(1));
    }

    @Test
    void differentClientAddressesDoNotShareABucket() throws Exception {

        MockHttpServletRequest first = request("POST", "/api/auth/login");
        first.setRemoteAddr("203.0.113.1");
        rateLimitFilter.doFilter(first, response, filterChain);

        MockHttpServletRequest second = request("POST", "/api/auth/login");
        second.setRemoteAddr("203.0.113.2");
        rateLimitFilter.doFilter(second, response, new MockFilterChain());

        List<String> identities = capturedIdentities();
        assertNotEquals(identities.get(0), identities.get(1));
    }

    // ---------------------------------------------------------------------
    // Proxy handling
    // ---------------------------------------------------------------------

    @Test
    void readsTheClientAddressFromTheRightmostForwardedEntry() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(PROXY_ADDRESS);
        request.addHeader("X-Forwarded-For", "198.51.100.4, " + REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void aForgedForwardedEntryCannotChooseTheBucket() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(PROXY_ADDRESS);

        // The caller sent "1.2.3.4"; the proxy appended what it actually saw
        request.addHeader("X-Forwarded-For", "1.2.3.4, " + REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void forwardedHeaderIsIgnoredWhenNoProxyIsTrusted() throws Exception {

        ReflectionTestUtils.setField(rateLimitProperties, "trustedProxyHops", 0);

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(PROXY_ADDRESS);
        request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + PROXY_ADDRESS, capturedIdentity());
    }

    @Test
    void repeatedForwardedHeaderLinesAreReadAsOneList() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(PROXY_ADDRESS);

        /*
          HTTP allows one header to arrive as several lines meaning one joined
          list. getHeader() would return only the first - the line the CALLER
          wrote - handing them a bucket of their own choosing.
        */
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Forwarded-For", REAL_CLIENT_ADDRESS);

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:" + REAL_CLIENT_ADDRESS, capturedIdentity());
    }

    @Test
    void aSourcePortDoesNotSplitOneCallerAcrossManyBuckets() throws Exception {

        // A per-connection port in the address would mint a fresh bucket per
        // request and silently disable IP limiting altogether
        MockHttpServletRequest withPort = request("POST", "/api/auth/login");
        withPort.setRemoteAddr(PROXY_ADDRESS);
        withPort.addHeader("X-Forwarded-For", REAL_CLIENT_ADDRESS + ":54321");

        MockHttpServletRequest withAnotherPort = request("POST", "/api/auth/login");
        withAnotherPort.setRemoteAddr(PROXY_ADDRESS);
        withAnotherPort.addHeader("X-Forwarded-For", REAL_CLIENT_ADDRESS + ":61000");

        rateLimitFilter.doFilter(withPort, response, filterChain);
        rateLimitFilter.doFilter(withAnotherPort, response, new MockFilterChain());

        List<String> identities = capturedIdentities();
        assertEquals("ip:" + REAL_CLIENT_ADDRESS, identities.get(0));
        assertEquals(identities.get(0), identities.get(1));
    }

    @Test
    void bracketedIpv6AddressesKeepTheirAddressAndLoseTheirPort() throws Exception {

        MockHttpServletRequest request = request("POST", "/api/auth/login");
        request.setRemoteAddr(PROXY_ADDRESS);
        request.addHeader("X-Forwarded-For", "[2001:db8::1]:443");

        rateLimitFilter.doFilter(request, response, filterChain);

        assertEquals("ip:2001:db8::1", capturedIdentity());
    }

    // ---------------------------------------------------------------------
    // Tiers
    // ---------------------------------------------------------------------

    @Test
    void reportCreationAndUploadsUseTheExpensiveTier() {
        assertTier("POST", "/api/reports", "ai", 5);
        assertTier("POST", "/api/files/upload", "ai", 5);
        assertTier("POST", "/api/cleanup-assignments/12/upload-image", "ai", 5);
        assertTier("POST", "/api/cleanup-proposals/assignment/12", "ai", 5);
        assertTier("PUT", "/api/cleanup-proposals/8", "ai", 5);
        assertTier("POST", "/api/cleanup-activity-logs/assignment/12", "ai", 5);
    }

    @Test
    void credentialEndpointsUseTheAuthTier() {
        assertTier("POST", "/api/auth/login", "auth", 10);
        assertTier("POST", "/api/auth/register", "auth", 10);
        assertTier("PATCH", "/api/account/password", "auth", 10);
    }

    @Test
    void communityWritesUseTheCommunityTier() {
        assertTier("POST", "/api/votes", "community", 30);
        assertTier("POST", "/api/comments/report/4", "community", 30);
        assertTier("DELETE", "/api/comments/9", "community", 30);
        assertTier("POST", "/api/public-feed/4/like", "community", 30);
    }

    @Test
    void staffConsolesUseTheStaffTier() {
        assertTier("GET", "/api/admin/dashboard", "staff", 60);
        assertTier("GET", "/api/cleanup-approvals/proposal-queue", "staff", 60);
        assertTier("POST", "/api/municipal-corporations", "staff", 60);
    }

    @Test
    void publicReadsFallThroughToTheGenerousTier() {
        assertTier("GET", "/api/reports", "pub", 100);
        assertTier("GET", "/api/public-feed", "pub", 100);
        assertTier("GET", "/api/leaderboard", "pub", 100);
        assertTier("GET", "/api/analytics/platform-impact", "pub", 100);
        assertTier("GET", "/api/comments/report/4", "pub", 100); // reads are not community writes
    }

    @Test
    void percentEncodedPathsCannotEscapeIntoAGenerousTier() throws Exception {

        /*
          getRequestURI() is never decoded by the container, but Spring MVC and
          Spring Security both match the DECODED path - so "/api/report%73"
          reaches the report controller. Matched raw, it misses the "ai" tier and
          falls through to "pub": 100 Gemini/Cloudinary calls a minute instead of
          5. StrictHttpFirewall does not block a bare %XX of an ordinary letter.

          Driven through doFilter rather than resolve(), because the decoding is
          the filter's job - asserting on resolve() alone would pass either way.
        */
        rateLimitFilter.doFilter(request("POST", "/api/report%73"), response, filterChain);

        assertEquals("ai", capturedScope());
    }

    @Test
    void percentEncodedCredentialPathsStayInTheAuthTier() throws Exception {

        rateLimitFilter.doFilter(request("POST", "/api/%61uth/login"), response, filterChain);

        assertEquals("auth", capturedScope());
    }

    // ---------------------------------------------------------------------
    // Exemptions
    // ---------------------------------------------------------------------

    @Test
    void theColdStartPingIsNeverThrottled() throws Exception {

        // The frontend polls this every 1.5s for up to two minutes while the
        // free-plan container wakes up - throttling it would break warm-up
        rateLimitFilter.doFilter(request("GET", "/api/health"), response, filterChain);

        verifyNoInteractions(rateLimitService);
        assertNotNull(filterChain.getRequest(), "the request must still be served");
    }

    @Test
    void preflightRequestsAreNeverThrottled() throws Exception {

        rateLimitFilter.doFilter(request("OPTIONS", "/api/reports"), response, filterChain);

        verifyNoInteractions(rateLimitService);
        assertNotNull(filterChain.getRequest());
    }

    @Test
    void nothingIsThrottledWhenTheFeatureIsSwitchedOff() throws Exception {

        ReflectionTestUtils.setField(rateLimitProperties, "enabled", false);

        rateLimitFilter.doFilter(request("POST", "/api/reports"), response, filterChain);

        verifyNoInteractions(rateLimitService);
        assertNotNull(filterChain.getRequest());
    }

    // ---------------------------------------------------------------------
    // The 429
    // ---------------------------------------------------------------------

    @Test
    void rejectedRequestsGetA429WithRetryAfterAndTheProjectErrorShape() throws Exception {

        when(rateLimitService.registerRequest(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(37L);

        rateLimitFilter.doFilter(request("POST", "/api/reports"), response, filterChain);

        assertEquals(429, response.getStatus());
        assertEquals("37", response.getHeader("Retry-After"));
        assertEquals("application/json", response.getContentType());

        String body = response.getContentAsString();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("\"message\""), body);
        assertTrue(body.contains("37 seconds"), body);
    }

    @Test
    void rejectedRequestsNeverReachTheRestOfTheChain() throws Exception {

        when(rateLimitService.registerRequest(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(12L);

        rateLimitFilter.doFilter(request("POST", "/api/reports"), response, filterChain);

        // Nothing downstream ran, so no Gemini call and no Cloudinary upload
        assertNull(filterChain.getRequest());
    }

    @Test
    void allowedRequestsContinueDownTheChainUntouched() throws Exception {

        rateLimitFilter.doFilter(request("GET", "/api/reports"), response, filterChain);

        assertEquals(200, response.getStatus());
        assertNull(response.getHeader("Retry-After"));
        assertNotNull(filterChain.getRequest());
    }

    // ---------------------------------------------------------------------
    // Single execution
    // ---------------------------------------------------------------------

    @Test
    void oneRequestSpendsExactlyOneSlotWhenTwoCopiesOfTheFilterAreChained() throws Exception {

        /*
          The filter is a @Component, so the servlet container registers a copy
          of its own alongside the one in the security chain - the container copy
          wrapping the security chain copy, exactly as modelled here. Only the
          outer one may count, otherwise every request costs two slots.
        */
        MockFilterChain innerChain = new MockFilterChain();
        FilterChain outerChain = (req, res) -> rateLimitFilter.doFilter(req, res, innerChain);

        rateLimitFilter.doFilter(request("GET", "/api/reports"), response, outerChain);

        verify(rateLimitService, times(1))
                .registerRequest(anyString(), anyString(), anyInt(), anyInt());

        assertNotNull(innerChain.getRequest(), "the request must still be served");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(REAL_CLIENT_ADDRESS);
        return request;
    }

    private void signIn(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private void assertTier(String method, String path, String expectedScope, int expectedLimit) {

        RateLimitProperties.Rule rule = rateLimitProperties.resolve(method, path);

        assertNotNull(rule, method + " " + path + " should be rate limited");
        assertEquals(expectedScope, rule.scope(), method + " " + path);
        assertEquals(expectedLimit, rule.limit(), method + " " + path);
        assertTrue(rule.limit() > 0);
    }

    private String capturedIdentity() {
        return capturedIdentities().get(0);
    }

    private List<String> capturedIdentities() {
        ArgumentCaptor<String> identityCaptor = ArgumentCaptor.forClass(String.class);

        verify(rateLimitService, org.mockito.Mockito.atLeastOnce())
                .registerRequest(anyString(), identityCaptor.capture(), anyInt(), anyInt());

        return identityCaptor.getAllValues();
    }

    /** The tier the filter actually resolved, as opposed to what resolve() returns. */
    private String capturedScope() {
        ArgumentCaptor<String> scopeCaptor = ArgumentCaptor.forClass(String.class);

        verify(rateLimitService, org.mockito.Mockito.atLeastOnce())
                .registerRequest(scopeCaptor.capture(), anyString(), anyInt(), anyInt());

        return scopeCaptor.getValue();
    }
}
