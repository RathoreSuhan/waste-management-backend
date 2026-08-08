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
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Public paths that don't need JWT validation
        return path.startsWith("/api/auth/") ||     // Login & Register
               path.startsWith("/api/files/") ||    // File viewing
               path.startsWith("/api/public-feed") ||
               path.startsWith("/api/leaderboard") ||
               path.equals("/actuator/health");     // Health check
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

            sendUnauthorizedResponse(
                    response,
                    "JWT token has expired. Please log in again."
            );
        }

        // Invalid JWT (signature, malformed, etc.)
        catch (JwtException ex) {

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