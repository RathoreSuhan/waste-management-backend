package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LeaderboardResponse;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.ContextConfiguration;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Cache semantics for the leaderboard boards, exercised through the real
 * caching proxy over a local cache manager - no Redis needed, because what
 * is under test is the @Cacheable contract, not the connection.
 *
 * Locked in here, because the production incident shipped through this
 * layer: an EMPTY board is a non-null response and IS cached, so once the
 * matching was fixed the stale empty entry had to be evicted before the
 * corrected result could appear. Casing variants must also share one key,
 * so a differently-cased request can never answer from a sibling entry
 * computed under different rules.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        LeaderboardServiceImpl.class,
        LeaderboardCacheTest.CacheConfig.class,
})
class LeaderboardCacheTest {

    private static final String BOARD_CACHE = "leaderboard_top";

    @Configuration
    @EnableCaching
    static class CacheConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private CleanupAssignmentRepository cleanupAssignmentRepository;

    @MockitoBean
    private IndianLocationService indianLocationService;

    // The proxy exposes the interface: the caching interceptor wraps the
    // service in a JDK dynamic proxy, so inject the contract, not the class
    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private CacheManager cacheManager;

    private User cleaner(long id, String name, int points) {
        Mockito.lenient().when(cleanupAssignmentRepository.countByCleanerAndStatus(
                        any(User.class), eq(AssignmentStatus.COMPLETED)))
                .thenReturn(1L);

        return User.builder()
                .id(id)
                .name(name)
                .email("cleaner" + id + "@example.com")
                .role(Role.ROLE_CLEANER)
                .state("Punjab")
                .city("Mohali")
                .district("S.A.S Nagar")
                .rewardPoints(points)
                .build();
    }

    @Test
    void anEmptyDistrictResultIsCachedAndOneKeyEvictionRecomputesIt() {

        // First read: the finder (still) answers empty, and that empty board
        // is cached under the casing-folded district key
        when(userRepository.findTop10InDistrict(
                eq(Role.ROLE_CLEANER), eq("Punjab"), eq("S.A.S Nagar"), any(Pageable.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(cleaner(1L, "Mohali Cleaner", 50)));

        LeaderboardResponse firstRead = leaderboardService.getDistrictLeaderboard("Punjab", "S.A.S Nagar");
        assertTrue(firstRead.getLeaderboard().isEmpty());
        assertNotNull(cacheEntry("district:punjab|s.a.s nagar"));

        // A differently-cased request is the SAME key: the cached empty
        // board answers again and the finder is not asked twice
        LeaderboardResponse secondRead = leaderboardService.getDistrictLeaderboard("punjab", "s.a.s nagar");
        assertTrue(secondRead.getLeaderboard().isEmpty());
        verify(userRepository, times(1)).findTop10InDistrict(any(), any(), any(), any());

        // Evicting that one key - the same mechanism the reward and approval
        // paths apply to the whole cache - makes the next read recompute
        evictBoardEntry("district:punjab|s.a.s nagar");

        LeaderboardResponse recomputed = leaderboardService.getDistrictLeaderboard("Punjab", "S.A.S Nagar");
        assertEquals(1, recomputed.getLeaderboard().size());
        assertEquals("Mohali Cleaner", recomputed.getLeaderboard().get(0).getCleanerName());
        verify(userRepository, times(2)).findTop10InDistrict(any(), any(), any(), any());
    }

    @Test
    void theCityUlbPathCachesUnderItsSubmittedName() {

        User legacyCityCleaner = cleaner(2L, "Legacy City Cleaner", 50);

        when(indianLocationService.matchByCityName("S.A.S.Nagar - Mohali"))
                .thenReturn(new IndianLocationService.NameMatch(
                        new IndianLocationService.ResolvedLocation(
                                "Punjab", "S.A.S Nagar", "S.A.S.Nagar - Mohali", "248325"),
                        null));
        when(userRepository.findTop10ByUlbCode(
                eq(Role.ROLE_CLEANER), eq("248325"), any(Pageable.class)))
                .thenReturn(List.of(legacyCityCleaner));

        LeaderboardResponse firstRead = leaderboardService.getCityLeaderboard("S.A.S.Nagar - Mohali");
        assertEquals("S.A.S.Nagar - Mohali", firstRead.getLocation());

        // The second read is served from the cache - the directory and the
        // finder are not consulted again
        LeaderboardResponse secondRead = leaderboardService.getCityLeaderboard("S.A.S.Nagar - Mohali");
        assertEquals("S.A.S.Nagar - Mohali", secondRead.getLocation());
        verify(userRepository, times(1)).findTop10ByUlbCode(any(), any(), any());
        verify(indianLocationService, times(1)).matchByCityName(any());
        assertNotNull(cacheEntry("city:s.a.s.nagar - mohali"));
    }

    private Cache.ValueWrapper cacheEntry(String key) {
        Cache cache = cacheManager.getCache(BOARD_CACHE);
        assertNotNull(cache);
        return cache.get(key);
    }

    private void evictBoardEntry(String key) {
        Cache cache = cacheManager.getCache(BOARD_CACHE);
        assertNotNull(cache);
        cache.evict(key);
    }
}
