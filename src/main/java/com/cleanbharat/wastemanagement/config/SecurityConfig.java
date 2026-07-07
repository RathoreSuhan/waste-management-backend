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

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http

                // Disable CSRF for REST APIs
                .csrf(csrf -> csrf.disable())

                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Authorization Rules
                .authorizeHttpRequests(auth -> auth

                        // Public APIs accessible without login
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/files/**",
                                "/api/public-feed/**",

                                // National leaderboard
                                "/api/leaderboard",

                                // State leaderboard
                                "/api/leaderboard/state/**",

                                // City leaderboard
                                "/api/leaderboard/city/**"
                        ).permitAll()

                        // Admin Only APIs
                        .requestMatchers("/api/municipal-corporations/**")
                        .hasRole("ADMIN")

                        // Admin Portal APIs
                        .requestMatchers("/api/admin/**")
                        .hasRole("ADMIN")

                        // Citizen Only APIs
                        .requestMatchers("/api/votes/**")
                        .hasRole("CITIZEN")

                        // All other APIs require login
                        .anyRequest().authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }
}