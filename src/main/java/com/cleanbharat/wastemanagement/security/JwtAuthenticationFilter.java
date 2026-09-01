package com.cleanbharat.wastemanagement.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    /**
     * Skip JWT validation for public endpoints.
     * Public endpoints don't require authentication, so filter shouldn't process them.
     *
     * The public feed is deliberately NOT skipped, even though reading it is
     * open to everyone.
     *
     * Skipping a path does not merely make it public - it stops the token
     * from being read at all, so the request arrives anonymous even when the
     * caller sent a perfectly good one. Two things depend on knowing who is
     * asking: liking a cleanup is recorded against an account, and each
     * story reports whether this reader's own like stands. While this path
     * was skipped the like endpoint was refused for everyone (SecurityConfig
     * requires authentication, and with no entry point configured Spring
     * answers 403), and likedByMe came back false for everybody.
     *
     * Running the filter costs the anonymous case nothing: a request with no
     * Authorization header passes straight through below.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Public paths that don't need JWT validation
        return path.startsWith("/api/auth/") ||     // Login & Register
               path.startsWith("/api/files/") ||    // File viewing
               path.startsWith("/api/leaderboard") ||
               path.equals("/api/health") ||        // Wake-up ping, sent before sign-in
               path.equals("/actuator/health");     // Health check
    }

    /**
     * Whether a bad token should be tolerated rather than rejected.
     *
     * On endpoints that anyone may read, an expired token is not a reason to
     * refuse the request - the caller is simply treated as a stranger and
     * sees what a stranger sees. Rejecting instead would mean a visitor
     * whose session quietly lapsed could no longer open a public success
     * story at all, which is a worse outcome than an unfilled heart.
     *
     * Endpoints that record something against an account are excluded: there
     * a lapsed session must be reported so the page can ask the visitor to
     * sign in again, rather than silently dropping what they did.
     */
    private boolean toleratesAnonymous(HttpServletRequest request) {

        String path = request.getRequestURI();

        if (!path.startsWith("/api/public-feed")) {
            return false;
        }

        // Liking belongs to an account, so it must not degrade to anonymous
        return !("POST".equalsIgnoreCase(request.getMethod())
                && path.endsWith("/like"));
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String email;

        // No Authorization header → continue the request (SecurityConfig will handle authorization)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract JWT token
        jwt = authHeader.substring(7);

        try {

            // Extract email from JWT
            email = jwtService.extractUsername(jwt);

            // Authenticate only if SecurityContext is still empty
            if (email != null &&
                    SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(email);

                // Validate token before authenticating
                if (jwtService.validateToken(jwt, userDetails.getUsername())) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    // Store authenticated user
                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authToken);
                }
            }

            // Continue request
            filterChain.doFilter(request, response);

        }

        // JWT has expired
        catch (ExpiredJwtException ex) {

            /*
              On a publicly readable path the request carries on without an
              identity, so a lapsed session still sees the page. Elsewhere
              the caller is told, so they can sign in again.
            */
            if (toleratesAnonymous(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            sendUnauthorizedResponse(
                    response,
                    "JWT token has expired. Please log in again."
            );
        }

        // Invalid JWT (signature, malformed, etc.)
        catch (JwtException ex) {

            // Same reasoning as above
            if (toleratesAnonymous(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            sendUnauthorizedResponse(
                    response,
                    "Invalid JWT token."
            );
        }

    }

    /**
     * Sends a standard JSON response for authentication failures.
     */
    private void sendUnauthorizedResponse(
            HttpServletResponse response,
            String message
    ) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");

        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper.writeValue(
                response.getWriter(),
                java.util.Map.of(
                        "status", 401,
                        "message", message
                )
        );
    }
}