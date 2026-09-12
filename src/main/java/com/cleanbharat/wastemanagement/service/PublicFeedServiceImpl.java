package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LikeResponse;
import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import com.cleanbharat.wastemanagement.util.PaginationUtil;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PublicFeedServiceImpl implements PublicFeedService {

    // Repository for completed cleanup assignments
    private final CleanupAssignmentRepository assignmentRepository;

    private final PublicFeedAnalyticsService publicFeedAnalyticsService;

    // Analytics rows for a whole page, fetched in one query
    private final PublicFeedAnalyticsRepository analyticsRepository;

    /*
      Which reports a reader has already liked, for a whole page in one
      query. Kept here rather than behind PublicFeedAnalyticsService
      because that service answers for one cleanup at a time, which is the
      shape the like/unlike path needs and the shape this page cannot use.
    */
    private final VoteRepository voteRepository;

    // Resolves the signed-in user, who a like belongs to
    private final UserRepository userRepository;


    @Override
    public PageResponse<PublicFeedResponse> getPublicFeed(int page, int size) {

        // Resolved once for the whole page rather than per story
        User currentUser = currentUserOrNull();

        /*
          No sort is passed to the pageable: the feed query carries its own
          ORDER BY with NULLS LAST, which a Sort object cannot express. See
          CleanupAssignmentRepository.findCompletedVerifiedAssignmentsPaged.
        */
        Pageable pageable = PaginationUtil.resolve(page, size);

        Page<CleanupAssignment> assignments =
                assignmentRepository.findCompletedVerifiedAssignmentsPaged(pageable);

        List<CleanupAssignment> rows = assignments.getContent();

        /*
          ====================================================================
          Counters fetched once per page rather than once per story
          ====================================================================

          Each card shows its view, like and share totals, and whether this
          reader has already appreciated it. Resolved through mapToResponse
          one row at a time, that was two queries per story - twenty for a
          page of ten - for data a single IN query returns in one go.

          Both maps are empty for an anonymous reader, who owns no likes.
        */
        Map<Long, PublicFeedAnalytics> analyticsByAssignmentId =
                loadAnalytics(rows);

        Set<Long> likedReportIds = loadLikedReportIds(rows, currentUser);

        return PageResponse.from(
                assignments,
                assignment -> mapToResponse(
                        assignment,
                        analyticsByAssignmentId,
                        likedReportIds
                )
        );
    }

    /**
     * Analytics for every cleanup on the page, keyed by assignment id.
     *
     * The association loaded through JOIN FETCH is the same instance the
     * map is keyed on, so the lookup in the mapper is an identity match.
     */
    private Map<Long, PublicFeedAnalytics> loadAnalytics(
            List<CleanupAssignment> assignments
    ) {

        if (assignments.isEmpty()) {
            return Map.of();
        }

        return analyticsRepository
                .findByCleanupAssignmentIn(assignments)
                .stream()
                .collect(Collectors.toMap(
                        analytics -> analytics.getCleanupAssignment().getId(),
                        analytics -> analytics
                ));
    }

    /**
     * Which of the page's reports this reader has already liked.
     *
     * Skipped entirely when nobody is signed in - an anonymous reader cannot
     * have appreciated anything, so there is no question to ask and no query
     * worth running.
     */
    private Set<Long> loadLikedReportIds(
            List<CleanupAssignment> assignments,
            User currentUser
    ) {

        if (currentUser == null || assignments.isEmpty()) {
            return Set.of();
        }

        List<GarbageReport> reports = assignments
                .stream()
                .map(CleanupAssignment::getReport)
                .toList();

        return voteRepository
                .findByUserAndReportInAndLikedTrue(currentUser, reports)
                .stream()
                .map(vote -> vote.getReport().getId())
                .collect(Collectors.toSet());
    }

    @Override
    public PublicFeedResponse getPublicFeedByReportId(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found for report id: " + reportId));

        /*
          The single-story view resolves its two lookups directly. It is one
          row, so the batch form the list uses would cost the same and only
          add indirection.
        */
        User currentUser = currentUserOrNull();

        PublicFeedAnalytics analytics =
                publicFeedAnalyticsService.getAnalytics(assignment);

        Set<Long> likedReportIds = loadLikedReportIds(List.of(assignment), currentUser);

        return mapToResponse(
                assignment,
                Map.of(assignment.getId(), analytics),
                likedReportIds
        );
    }


    @Override
    public void incrementView(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found."));

        publicFeedAnalyticsService.incrementViewCount(assignment);
    }

    @Override
    public LikeResponse toggleLike(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found."));

        /*
         * A like has to belong to somebody.
         *
         * Security only permits this endpoint to signed-in callers, so
         * reaching here without a user would mean the two have drifted
         * apart. Failing loudly is safer than silently recording an
         * ownerless like.
         */
        User user = currentUserOrNull();

        if (user == null) {
            throw new ResourceNotFoundException(
                    "Signed-in user required to appreciate a cleanup."
            );
        }

        boolean liked = publicFeedAnalyticsService.toggleLike(assignment, user);

        return LikeResponse.builder()
                .reportId(reportId)
                .likeCount(publicFeedAnalyticsService.getAnalytics(assignment).getLikeCount())
                .liked(liked)
                .build();
    }


    @Override
    public void incrementShare(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found."));

        publicFeedAnalyticsService.incrementShareCount(assignment);
    }

    /**
     * The user behind the current request, or null when nobody is signed in.
     *
     * The feed is deliberately readable without an account, so an absent
     * user is a normal case here and not an error. Spring represents an
     * anonymous caller with a token named "anonymousUser" rather than with
     * an empty context, so that name has to be excluded too - otherwise it
     * would be looked up as though it were an email address.
     */
    private User currentUserOrNull() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElse(null);
    }

    /**
     * Converts CleanupAssignment into PublicFeedResponse.
     *
     * `analyticsByAssignmentId` and `likedReportIds` are supplied by the
     * caller instead of being looked up here. That is the whole point of
     * the change: a lookup in this method was a query per story, and one
     * page of ten stories cost twenty of them. Both are assembled once for
     * the page and handed in.
     *
     * A missing analytics row would be a broken invariant - analytics are
     * created when the assignment is verified - so it is reported rather
     * than silently rendered as zero. `likedReportIds` being empty is
     * normal: it means the reader is signed out, or has liked nothing.
     */
    private PublicFeedResponse mapToResponse(
            CleanupAssignment assignment,
            Map<Long, PublicFeedAnalytics> analyticsByAssignmentId,
            Set<Long> likedReportIds
    ) {

        // Community appreciation analytics
        PublicFeedAnalytics analytics = analyticsByAssignmentId.get(assignment.getId());

        if (analytics == null) {
            throw new ResourceNotFoundException("Public feed analytics not found.");
        }

        // Whether this reader's own like of the cleanup stands
        boolean likedByMe = likedReportIds.contains(assignment.getReport().getId());


        return PublicFeedResponse.builder()

                // Garbage report details
                .reportId(assignment.getReport().getId())
                .reportTitle(assignment.getReport().getTitle())
                .reportDescription(assignment.getReport().getDescription())

                // Before & After cleanup images
                .beforeImageUrl(assignment.getReport().getImageUrl())
                .afterImageUrl(assignment.getCleanupImageUrl())

                // Cleanup location
                .address(assignment.getReport().getAddress())
                .landmark(assignment.getReport().getLandmark())
                .city(assignment.getReport().getCity())
                .state(assignment.getReport().getState())

                // Cleaner information
                .cleanerName(assignment.getCleaner().getName())
                .cleanerType(assignment.getCleaner().getCleanerType().name())

                // Municipal Corporation
                .municipalCorporationName(
                        assignment.getAssignedMunicipalCorporation()
                                .getOrganizationName()
                )

                // Cleanup completion time
                .cleanupCompletedTime(assignment.getCompletedAt())

                // Final report status
                .reportStatus(
                        assignment.getReport()
                                .getStatus()
                                .name()
                )

                // AI verification details
                .aiVerified(assignment.getAiVerified())
                .aiConfidence(assignment.getAiConfidence())
                .aiRemarks(assignment.getAiRemarks())

                // Public Feed Analytics
                .viewCount(analytics.getViewCount())
                .likeCount(analytics.getLikeCount())
                .shareCount(analytics.getShareCount())

                // This reader's own appreciation
                .likedByMe(likedByMe)

                .build();

    }
}