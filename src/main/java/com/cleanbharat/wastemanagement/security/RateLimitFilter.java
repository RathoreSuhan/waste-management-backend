package com.cleanbharat.wastemanagement.security;

import com.cleanbharat.wastemanagement.config.RateLimitProperties;
import com.cleanbharat.wastemanagement.exception.ErrorResponse;
import com.cleanbharat.wastemanagement.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Collections;

/**
 * ============================================================
 *  Rate Limit Filter
 * ============================================================
 *
 *  Sheds abusive traffic before it reaches a controller - and for the "ai" tier,
 *  before it reaches Gemini or Cloudinary, which is the whole reason this runs
 *  in the filter chain rather than in the service layer.
 *
 *  PLACEMENT
 *  ---------
 *  Registered with addFilterAfter(this, JwtAuthenticationFilter.class), so:
 *    - JwtAuthenticationFilter has already populated the SecurityContext, which
 *      means an authenticated caller is counted by principal, not by the IP they
 *      happen to share with a whole office or mobile carrier
 *    - AuthorizationFilter has not run yet, so the limit applies even to callers
 *      who would have been rejected with 403 - probing costs them their quota
 *
 *  IDENTITY
 *  --------
 *  Taken only from the SecurityContext or the server connection. Never from a
 *  request parameter, body field, or client-chosen header, all of which the
 *  caller controls and could vary per request to get an unlimited number of
 *  buckets.
 *
 *  RESPONSE
 *  --------
 *  Written here by hand. A servlet filter runs outside the DispatcherServlet, so
 *  @RestControllerAdvice never sees it - exactly why JwtAuthenticationFilter
 *  writes its own 401 the same way. The body reuses the project's ErrorResponse,
 *  so a 429 looks like every other API error to the frontend.
 * ============================================================
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";

    private static final String TOO_MANY_REQUESTS_MESSAGE =
            "Too many requests. Please slow down and try again in %d seconds.";

    private final RateLimitProperties rateLimitProperties;

    private final RateLimitService rateLimitService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Decodes and normalizes the path the same way Spring does before matching.
     *
     * getRequestURI() is the RAW target: the servlet container never decodes it.
     * Spring MVC and Spring Security both match on the decoded path, so
     * "/api/%72eports" routes to the report controller while a raw comparison
     * against "/api/reports" misses - and a missed tier falls through to the
     * generous pub allowance, handing an attacker 100 Gemini calls a minute
     * instead of 5. Matching the framework's own view of the path closes that.
     */
    private static final UrlPathHelper PATH_HELPER = UrlPathHelper.defaultInstance;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        RateLimitProperties.Rule rule = rateLimitProperties.isEnabled()
                ? rateLimitProperties.resolve(
                        request.getMethod(),
                        PATH_HELPER.getPathWithinApplication(request))
                : null;

        if (rule == null) { // exempt path, or limiting switched off entirely
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = rateLimitService.registerRequest(
                rule.scope(),
                resolveIdentity(request),
                rule.limit(),
                rateLimitProperties.getWindowSeconds()
        );

        if (retryAfterSeconds > 0) {
            sendTooManyRequestsResponse(response, retryAfterSeconds);
            return; // chain stops here - no controller, no Gemini, no Cloudinary
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Who is being counted: the authenticated principal when there is one,
     * otherwise the client IP.
     */
    private String resolveIdentity(HttpServletRequest request) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        /*
          An anonymous token carries the literal name "anonymousUser", which would
          collapse every unauthenticated visitor into a single shared bucket, so it
          has to fall through to the IP branch.
        */
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {

            return "user:" + authentication.getName();
        }

        return "ip:" + resolveClientIp(request);
    }

    /**
     * The application sits behind a hosting proxy, so getRemoteAddr() is that
     * proxy and on its own would put every anonymous caller in one bucket.
     *
     * Entries are read from the RIGHT of X-Forwarded-For because the proxy
     * appends the peer it actually observed. Anything further left was supplied
     * by the caller and can claim any address it likes, so trusting the leftmost
     * entry - the common mistake - would make the limit trivially bypassable.
     */
    private String resolveClientIp(HttpServletRequest request) {

        int trustedHops = rateLimitProperties.getTrustedProxyHops();

        if (trustedHops > 0) {

            /*
              Joined across every header line, not read with getHeader().
              X-Forwarded-For may legitimately arrive as several repeated lines
              that mean one comma-joined list, and getHeader() returns only the
              first - which is the line the CLIENT wrote when the proxy adds its
              own. Reading that alone would hand the caller a bucket of their
              choosing, defeating the right-to-left rule below. Tomcat's
              RemoteIpValve and Spring's ForwardedHeaderFilter both join first.
            */
            String forwardedFor = String.join(
                    ",",
                    Collections.list(request.getHeaders(FORWARDED_FOR_HEADER))
            );

            if (!forwardedFor.isBlank()) {

                String[] entries = forwardedFor.split(",");
                int index = entries.length - trustedHops;

                if (index >= 0 && index < entries.length && !entries[index].isBlank()) {
                    return normalizeAddress(entries[index].trim());
                }
            }
        }

        String remoteAddress = request.getRemoteAddr();

        return remoteAddress != null ? normalizeAddress(remoteAddress) : "unknown";
    }

    /**
     * Strips a port and IPv6 brackets, so one caller is one bucket.
     *
     * Some proxies write the peer as host:port. The source port changes on every
     * connection, so leaving it in would fingerprint each request to a different
     * key and silently disable IP-based limiting altogether - the failure mode is
     * invisible, because the identity is hashed before it reaches Redis.
     */
    private static String normalizeAddress(String address) {

        // "[2001:db8::1]" or "[2001:db8::1]:54321"
        if (address.startsWith("[")) {

            int closing = address.indexOf(']');

            return closing > 0 ? address.substring(1, closing) : address;
        }

        int colon = address.indexOf(':');

        // Exactly one colon means IPv4:port; several means a bare IPv6 literal
        if (colon > 0 && address.indexOf(':', colon + 1) < 0) {
            return address.substring(0, colon);
        }

        return address;
    }

    /**
     * Mirrors JwtAuthenticationFilter.sendUnauthorizedResponse so both filters
     * produce the same { message, status } shape the frontend already reads.
     */
    private void sendTooManyRequestsResponse(
            HttpServletResponse response,
            long retryAfterSeconds
    ) throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

        // Standard header; SecurityConfig exposes it to the browser via CORS
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));

        response.setContentType("application/json");

        objectMapper.writeValue(
                response.getWriter(),
                new ErrorResponse(
                        TOO_MANY_REQUESTS_MESSAGE.formatted(retryAfterSeconds),
                        HttpStatus.TOO_MANY_REQUESTS.value()
                )
        );
    }
}
