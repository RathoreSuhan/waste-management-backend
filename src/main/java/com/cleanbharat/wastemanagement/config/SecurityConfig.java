package com.cleanbharat.wastemanagement.config;

import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
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

                        // Public endpoints - no authentication required
                        .requestMatchers(
                                "/api/auth/**",           // Login & Register
                                "/api/files/**",          // View uploaded images
                                "/api/public-feed/**",    // Public garbage reports feed

                                "/api/leaderboard",       // National leaderboard
                                "/api/leaderboard/state/**", // State leaderboard
                                "/api/leaderboard/city/**"   // City leaderboard
                        ).permitAll()

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