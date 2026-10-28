package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.config.SecurityConfig;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import com.cleanbharat.wastemanagement.security.RateLimitFilter;
import com.cleanbharat.wastemanagement.service.MunicipalCorporationService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security + JSON contract for the two municipal-contact lookup endpoints.
 *
 * The real SecurityConfig runs here. Both mocked request filters continue its
 * chain; the JWT filter additionally installs a citizen principal when this
 * test sends an Authorization header, standing in for its production token
 * verification without weakening or bypassing the real authorization rules.
 */
@WebMvcTest(MunicipalCorporationController.class)
@Import(SecurityConfig.class)
class MunicipalCorporationControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MunicipalCorporationService municipalCorporationService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    @BeforeEach
    void continueTheRealSecurityChainAndAuthenticateBearerRequests() throws Exception {
        doAnswer(invocation -> {
            var request = (jakarta.servlet.http.HttpServletRequest) invocation.getArgument(0);
            FilterChain chain = invocation.getArgument(2);

            if (request.getHeader("Authorization") != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "citizen@example.com",
                                null,
                                List.of(new SimpleGrantedAuthority(Role.ROLE_CITIZEN.name()))));
            }

            try {
                chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            } finally {
                SecurityContextHolder.clearContext();
            }
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());

        doAnswer(invocation -> {
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(rateLimitFilter).doFilter(any(), any(), any());
    }

    @Test
    void anAnonymousVisitorCannotReadAContactByUlbCode() throws Exception {
        // SecurityConfig's default anonymous entry point answers protected
        // MVC paths with 403 (the same posture the legacy /city path has).
        mockMvc.perform(get("/api/municipal-corporations/ulb/248325"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAuthenticatedCitizenCanReadTheContactByUlbCode() throws Exception {
        when(municipalCorporationService.getByUlbCode("248325"))
                .thenReturn(MunicipalCorporationResponse.builder()
                        .id(5L)
                        .city("Mohali")
                        .ulbCode("248325")
                        .organizationName("Municipal Corporation SAS Nagar Mohali")
                        .phone("0172-2220000")
                        .email("contact@mohali.gov.in")
                        .build());

        mockMvc.perform(get("/api/municipal-corporations/ulb/248325")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("Mohali"))
                .andExpect(jsonPath("$.ulbCode").value("248325"))
                .andExpect(jsonPath("$.organizationName")
                        .value("Municipal Corporation SAS Nagar Mohali"))
                .andExpect(jsonPath("$.phone").value("0172-2220000"))
                .andExpect(jsonPath("$.email").value("contact@mohali.gov.in"));

        verify(municipalCorporationService).getByUlbCode("248325");
    }

    @Test
    void theLegacyCityLookupRemainsAuthenticated() throws Exception {
        mockMvc.perform(get("/api/municipal-corporations/city/Mohali"))
                .andExpect(status().isForbidden());
    }
}
