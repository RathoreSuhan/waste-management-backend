package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.config.SecurityConfig;
import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import com.cleanbharat.wastemanagement.security.RateLimitFilter;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Verifies the directory remains readable before a visitor has a JWT. */
@WebMvcTest(LocationController.class)
@Import(SecurityConfig.class)
class LocationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IndianLocationService locationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void allowTheTwoMockedFiltersToContinueTheRealSecurityChain() throws Exception {
        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitFilter).doFilter(any(), any(), any());

        when(locationService.directory()).thenReturn(
                new ObjectMapper().readTree("""
                        {"counts":{"states":35},"states":[]}
                        """));
    }

    @Test
    void anAnonymousVisitorCanReadTheLocationDirectory() throws Exception {
        mockMvc.perform(get("/api/locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.states").value(35));
    }
}
