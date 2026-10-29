package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.config.SecurityConfig;
import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.dto.MyLeaderboardResponse;
import com.cleanbharat.wastemanagement.enums.BadgeType;
import com.cleanbharat.wastemanagement.enums.LeaderboardType;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.security.JwtAuthenticationFilter;
import com.cleanbharat.wastemanagement.security.RateLimitFilter;
import com.cleanbharat.wastemanagement.service.LeaderboardService;
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
 * Security contract for every leaderboard scope.
 *
 * The real SecurityConfig runs here. Both mocked request filters continue its
 * chain; the JWT filter additionally installs a cleaner principal when this
 * test sends an Authorization header, standing in for its production token
 * verification without weakening or bypassing the real authorization rules.
 *
 * The four read scopes are public - the page is reachable without an account,
 * so the district route must answer anonymously exactly like national, state
 * and city always have. /me is the one leaderboard endpoint that needs the
 * caller's identity, so an anonymous call stays refused.
 */
@WebMvcTest(LeaderboardController.class)
@Import(SecurityConfig.class)
class LeaderboardControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaderboardService leaderboardService;

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
                                "cleaner@example.com",
                                null,
                                List.of(new SimpleGrantedAuthority(Role.ROLE_CLEANER.name()))));
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

    private LeaderboardResponse aBoard(LeaderboardType type, String location) {
        return LeaderboardResponse.builder()
                .leaderboardType(type)
                .location(location)
                .leaderboard(List.of())
                .build();
    }

    @Test
    void anAnonymousVisitorCanReadTheNationalLeaderboard() throws Exception {
        when(leaderboardService.getPublicLeaderboard())
                .thenReturn(aBoard(LeaderboardType.NATIONAL, "India"));

        mockMvc.perform(get("/api/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboardType").value("NATIONAL"));
    }

    @Test
    void anAnonymousVisitorCanReadTheStateLeaderboard() throws Exception {
        when(leaderboardService.getStateLeaderboard("Bihar"))
                .thenReturn(aBoard(LeaderboardType.STATE, "Bihar"));

        mockMvc.perform(get("/api/leaderboard/state/Bihar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Bihar"));
    }

    @Test
    void anAnonymousVisitorCanReadTheCityLeaderboard() throws Exception {
        when(leaderboardService.getCityLeaderboard("Patna"))
                .thenReturn(aBoard(LeaderboardType.CITY, "Patna"));

        mockMvc.perform(get("/api/leaderboard/city/Patna"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value("Patna"));
    }

    @Test
    void anAnonymousVisitorCanReadTheDistrictLeaderboard() throws Exception {
        // The district route is the regression this suite locks in: it was
        // missing from permitAll and answered every visitor with a bare 403.
        when(leaderboardService.getDistrictLeaderboard("Bihar", "Gaya"))
                .thenReturn(aBoard(LeaderboardType.DISTRICT, "Bihar / Gaya"));

        mockMvc.perform(get("/api/leaderboard/district/Bihar/Gaya"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaderboardType").value("DISTRICT"))
                .andExpect(jsonPath("$.location").value("Bihar / Gaya"));

        verify(leaderboardService).getDistrictLeaderboard("Bihar", "Gaya");
    }

    @Test
    void anAnonymousVisitorIsStillRefusedThePersonalRanking() throws Exception {
        mockMvc.perform(get("/api/leaderboard/me"))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSignedInCleanerCanReadTheirPersonalRanking() throws Exception {
        when(leaderboardService.getMyRanking())
                .thenReturn(MyLeaderboardResponse.builder()
                        .rank(3)
                        .cleanerName("Test Cleaner")
                        .badge(BadgeType.SILVER)
                        .rewardPoints(250)
                        .completedCleanups(4L)
                        .aiVerifiedCleanups(4L)
                        .pointsToNextBadge(250)
                        .build());

        mockMvc.perform(get("/api/leaderboard/me")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value(3))
                .andExpect(jsonPath("$.cleanerName").value("Test Cleaner"))
                .andExpect(jsonPath("$.badge").value("SILVER"));
    }
}
