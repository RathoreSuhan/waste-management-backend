package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.config.SecurityConfig;
import com.cleanbharat.wastemanagement.dto.PasswordResetTokenResponse;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import com.cleanbharat.wastemanagement.security.RateLimitFilter;
import com.cleanbharat.wastemanagement.service.AccountService;
import com.cleanbharat.wastemanagement.service.PasswordRecoveryService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security contract for the profile-page password recovery endpoints.
 *
 * The real SecurityConfig runs here. Both mocked request filters continue
 * its chain; the JWT filter installs a principal with test-chosen
 * authorities when an Authorization header is sent, standing in for its
 * production token verification.
 *
 * Recovery changes credentials, so every endpoint must demand an
 * authenticated session - anonymous calls get Spring Security's refusal -
 * and the service must receive exactly the authenticated identity: the
 * account being reset is never a client choice.
 */
@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountService accountService;

    @MockitoBean
    private PasswordRecoveryService passwordRecoveryService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private RateLimitFilter rateLimitFilter;

    /** The authorities the mocked JWT filter installs for this test. */
    private List<SimpleGrantedAuthority> authoritiesToInstall =
            List.of(new SimpleGrantedAuthority(Role.ROLE_CITIZEN.name()));

    @BeforeEach
    void continueTheRealSecurityChainAndAuthenticateBearerRequests() throws Exception {
        doAnswer(invocation -> {
            var request = (jakarta.servlet.http.HttpServletRequest) invocation.getArgument(0);
            FilterChain chain = invocation.getArgument(2);

            if (request.getHeader("Authorization") != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                "account@example.com",
                                null,
                                authoritiesToInstall));
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
    void anAnonymousVisitorCannotRequestARecoveryOtp() throws Exception {
        mockMvc.perform(post("/api/account/password/recovery/otp"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousVisitorCannotVerifyARecoveryOtp() throws Exception {
        mockMvc.perform(post("/api/account/password/recovery/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void anAnonymousVisitorCannotConfirmARecoveryReset() throws Exception {
        mockMvc.perform(post("/api/account/password/recovery/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"t\",\"newPassword\":\"x\",\"confirmPassword\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aCitizenSessionRequestsAnOtpForItsOwnAccount() throws Exception {
        authoritiesToInstall = List.of(new SimpleGrantedAuthority(Role.ROLE_CITIZEN.name()));

        when(passwordRecoveryService.requestOtp("account@example.com", false))
                .thenReturn(SuccessResponse.builder()
                        .message("A six-digit recovery code has been sent.")
                        .timestamp(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/account/password/recovery/otp")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        verify(passwordRecoveryService).requestOtp("account@example.com", false);
    }

    @Test
    void anOfficerSessionRequestsAnOtpUnderItsOwnContext() throws Exception {
        authoritiesToInstall = List.of(new SimpleGrantedAuthority(Role.ROLE_MUNICIPAL_OFFICER.name()));

        when(passwordRecoveryService.requestOtp("account@example.com", true))
                .thenReturn(SuccessResponse.builder()
                        .message("A six-digit recovery code has been sent.")
                        .timestamp(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/account/password/recovery/otp")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk());

        verify(passwordRecoveryService).requestOtp("account@example.com", true);
    }

    @Test
    void aCitizenSessionVerifiesUnderItsOwnContext() throws Exception {
        authoritiesToInstall = List.of(new SimpleGrantedAuthority(Role.ROLE_CITIZEN.name()));

        when(passwordRecoveryService.verifyOtp("account@example.com", false, "123456"))
                .thenReturn(PasswordResetTokenResponse.builder()
                        .resetToken("issued-token")
                        .message("Code verified. Set your new password.")
                        .timestamp(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/account/password/recovery/verify")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"123456\"}"))
                .andExpect(status().isOk());

        verify(passwordRecoveryService).verifyOtp("account@example.com", false, "123456");
    }

    @Test
    void aCitizenSessionConfirmsWithTheUsersStoreSelected() throws Exception {
        authoritiesToInstall = List.of(new SimpleGrantedAuthority(Role.ROLE_CITIZEN.name()));

        when(passwordRecoveryService.confirmReset(
                eq("account@example.com"), eq(false), any()))
                .thenReturn(SuccessResponse.builder()
                        .message("Password reset successfully.")
                        .timestamp(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/account/password/recovery/confirm")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"token\",\"newPassword\":\"new-password\","
                                + "\"confirmPassword\":\"new-password\"}"))
                .andExpect(status().isOk());

        verify(passwordRecoveryService).confirmReset(
                eq("account@example.com"), eq(false), any());
    }

    @Test
    void anOfficerSessionConfirmsWithTheCorporationStoreSelected() throws Exception {
        authoritiesToInstall = List.of(new SimpleGrantedAuthority(Role.ROLE_MUNICIPAL_OFFICER.name()));

        when(passwordRecoveryService.confirmReset(
                eq("account@example.com"), eq(true), any()))
                .thenReturn(SuccessResponse.builder()
                        .message("Password reset successfully.")
                        .timestamp(LocalDateTime.now())
                        .build());

        mockMvc.perform(post("/api/account/password/recovery/confirm")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resetToken\":\"token\",\"newPassword\":\"new-password\","
                                + "\"confirmPassword\":\"new-password\"}"))
                .andExpect(status().isOk());

        verify(passwordRecoveryService).confirmReset(
                eq("account@example.com"), eq(true), any());
    }
}
