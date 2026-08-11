package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * CORS Configuration
     * 
     * Allows frontend (React) running on localhost:5173 (Vite dev server)
     * to communicate with this Spring Boot backend.
     * 
     * Without this, browser blocks cross-origin requests with CORS error.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Create CORS configuration
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow requests from frontend development server
        configuration.addAllowedOrigin("http://localhost:5173");
        
        // Also allow production frontend URL (add later when deployed)
        // configuration.addAllowedOrigin("https://cleanbharat.com");

        // Allow all HTTP methods (GET, POST, PUT, DELETE, etc.)
        configuration.addAllowedMethod("*");

        // Allow all request headers (Authorization, Content-Type, etc.)
        configuration.addAllowedHeader("*");

        // Allow credentials (cookies, JWT token in header)
        configuration.setAllowCredentials(true);

        // Apply CORS to all API endpoints
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Enable CORS - allows requests from frontend React app
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF for REST APIs (using JWT instead)
                .csrf(csrf -> csrf.disable())

                // Make session stateless - each request has its own JWT token
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization Rules - Define who can access which endpoints
                .authorizeHttpRequests(auth -> auth

                        /*
                          Declared FIRST, because rules are evaluated in order
                          and the public GET /api/reports/* rule further down
                          would otherwise swallow this path.

                          /api/reports/my resolves the report list from the
                          signed-in principal, so it can never be anonymous.
                        */
                        .requestMatchers(HttpMethod.GET, "/api/reports/my")
                        .authenticated()

                        // Public endpoints - no authentication required
                        .requestMatchers(
                                "/api/auth/**",           // Login & Register
                                "/api/files/**",          // View uploaded images
                                "/api/public-feed/**",    // Public garbage reports feed

                                "/api/leaderboard",       // National leaderboard
                                "/api/leaderboard/state/**", // State leaderboard
                                "/api/leaderboard/city/**"   // City leaderboard
                        ).permitAll()

                        /*
                          Publicly readable community data.

                          Visitors browse reports, the engagement ranking and
                          the discussion on a report without an account, which
                          is what makes the platform's work visible to people
                          who have not signed up yet.

                          Restricted to GET on purpose. Filing a report,
                          commenting, replying and voting all stay behind
                          authentication via the rules below - the frontend
                          prompts anonymous users to log in at that point.
                        */
                        .requestMatchers(HttpMethod.GET,
                                "/api/reports",                 // Report list
                                "/api/reports/*",               // A single report
                                "/api/comments/report/*",       // Discussion thread
                                "/api/analytics/trending",      // Engagement ranking
                                "/api/analytics/report/*"       // Per-report counts
                        ).permitAll()


                        /*
                          Municipal contact lookup for a city.

                          Declared BEFORE the admin rule below, because rules
                          are evaluated in order and the /** admin matcher
                          would otherwise swallow this path - the same
                          ordering trap noted on /api/reports/my above.

                          Any signed-in user may read this: a citizen needs
                          the corporation's phone number to chase their own
                          report, and a cleaner needs it to raise an issue
                          from the field.

                          Deliberately GET and the /city/ path only. The same
                          base path also serves POST, PUT and DELETE, so a
                          blanket rule here would let any citizen create,
                          edit or delete corporation records. Those stay
                          admin-only through the matcher below.
                        */
                        .requestMatchers(HttpMethod.GET, "/api/municipal-corporations/city/**")
                        .authenticated()

                        // Admin-only endpoints
                        .requestMatchers("/api/municipal-corporations/**")
                        .hasRole("ADMIN")

                        // Admin portal endpoints
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // Citizen-only endpoints
                        .requestMatchers("/api/votes/**")
                        .hasRole("CITIZEN")

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Add JWT filter - validates JWT token before other filters
                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}