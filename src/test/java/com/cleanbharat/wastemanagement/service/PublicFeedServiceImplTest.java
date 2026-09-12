package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the paged public feed.
 *
 * Focus: the counters for a whole page are fetched in batch queries - one
 * for analytics, one for this reader's likes - rather than per-story, and an
 * anonymous reader costs only the analytics query.
 */
@ExtendWith(MockitoExtension.class)
class PublicFeedServiceImplTest {

    @Mock private CleanupAssignmentRepository assignmentRepository;
    @Mock private PublicFeedAnalyticsService publicFeedAnalyticsService;
    @Mock private PublicFeedAnalyticsRepository analyticsRepository;
    @Mock private VoteRepository voteRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks
    private PublicFeedServiceImpl publicFeedService;

    @AfterEach
    void clearSecurityContext() {
        // Tests share one thread, so never leak a principal
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousReaderGetsAnalyticsInOneBatchAndNoLikeQuery() {

        CleanupAssignment first = story(1L, 101L, "Market chowk");
        CleanupAssignment second = story(2L, 102L, "Railway crossing");

        User cleaner = first.getCleaner();

        when(assignmentRepository.findCompletedVerifiedAssignmentsPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(first, second)));

        when(analyticsRepository.findByCleanupAssignmentIn(anyList()))
                .thenReturn(List.of(
                        analytics(first, 10L, 4L, 2L),
                        analytics(second, 7L, 1L, 0L)
                ));

        PageResponse<PublicFeedResponse> response =
                publicFeedService.getPublicFeed(0, 10);

        // Two stories, one analytics query - not a query per story
        verify(analyticsRepository).findByCleanupAssignmentIn(List.of(first, second));
        verify(voteRepository, never()).findByUserAndReportInAndLikedTrue(any(), anyList());

        assertEquals(2, response.content().size());
        assertEquals(10L, response.content().get(0).getViewCount());
        assertEquals(4L, response.content().get(0).getLikeCount());
        assertEquals("Market chowk", response.content().get(0).getReportTitle());

        // Nobody is signed in, so no heart can be filled
        assertFalse(response.content().get(0).getLikedByMe());

        // Cleaner details come from the fetched association
        assertEquals(cleaner.getName(), response.content().get(0).getCleanerName());
    }

    @Test
    void oversizedFeedPageIsClamped() {

        when(assignmentRepository.findCompletedVerifiedAssignmentsPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        publicFeedService.getPublicFeed(0, 999);

        org.mockito.ArgumentCaptor<Pageable> pageableCaptor =
                org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(assignmentRepository).findCompletedVerifiedAssignmentsPaged(pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }
@Test
    void signedInReaderLikesAreResolvedInOneBatchQuery() {

        CleanupAssignment liked = story(1L, 101L, "Market chowk");
        CleanupAssignment unliked = story(2L, 102L, "Railway crossing");

        User reader = User.builder()
                .id(77L)
                .name("Reader")
                .email("reader@example.com")
                .role(Role.ROLE_CITIZEN)
                .build();

        authenticateAs(reader.getEmail());

        when(userRepository.findByEmail(reader.getEmail())).thenReturn(java.util.Optional.of(reader));

        when(assignmentRepository.findCompletedVerifiedAssignmentsPaged(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(liked, unliked)));

        when(analyticsRepository.findByCleanupAssignmentIn(anyList()))
                .thenReturn(List.of(
                        analytics(liked, 1L, 1L, 0L),
                        analytics(unliked, 1L, 1L, 0L)
                ));

        // The reader has liked only the first story
        when(voteRepository.findByUserAndReportInAndLikedTrue(any(), anyList()))
                .thenReturn(List.of(Vote.builder()
                        .user(reader)
                        .report(liked.getReport())
                        .liked(true)
                        .build()));

        PageResponse<PublicFeedResponse> response =
                publicFeedService.getPublicFeed(0, 10);

        // One query for the whole page, not one per story
        verify(voteRepository).findByUserAndReportInAndLikedTrue(any(), anyList());

        assertTrue(response.content().get(0).getLikedByMe());
        assertFalse(response.content().get(1).getLikedByMe());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private PublicFeedAnalytics analytics(CleanupAssignment story, long views, long likes, long shares) {
        return PublicFeedAnalytics.builder()
                .id(story.getId())
                .cleanupAssignment(story)
                .viewCount(views)
                .likeCount(likes)
                .shareCount(shares)
                .build();
    }

    private CleanupAssignment story(long assignmentId, long reportId, String title) {

        User cleaner = User.builder()
                .id(assignmentId + 100)
                .name("Cleaner " + assignmentId)
                .email("cleaner" + assignmentId + "@example.com")
                .role(Role.ROLE_CLEANER)
                .cleanerType(CleanerType.MUNICIPAL)
                .build();

        MunicipalCorporation corporation = MunicipalCorporation.builder()
                .id(5L)
                .organizationName("Patna Municipal Corporation")
                .build();

        GarbageReport report = GarbageReport.builder()
                .id(reportId)
                .title(title)
                .description("Waste dumped near " + title + ".")
                .latitude(25.6)
                .longitude(85.1)
                .address(title + " Road")
                .city("Patna")
                .state("Bihar")
                .pincode("800001")
                .imageUrl("https://example.com/before-" + reportId + ".jpg")
                .status(ReportStatus.RESOLVED)
                .user(cleaner)
                .build();

        return CleanupAssignment.builder()
                .id(assignmentId)
                .report(report)
                .cleaner(cleaner)
                .assignedMunicipalCorporation(corporation)
                .status(AssignmentStatus.COMPLETED)
                .cleanupImageUrl("https://example.com/after-" + reportId + ".jpg")
                .completedAt(LocalDateTime.now())
                .aiVerified(true)
                .aiConfidence(0.98)
                .aiRemarks("Verified")
                .build();
    }

    /**
     * Puts a JWT-style principal in the context, which is all the service reads.
     */
    private void authenticateAs(String email) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(email, "n/a", List.of()));
        SecurityContextHolder.setContext(context);
    }
}