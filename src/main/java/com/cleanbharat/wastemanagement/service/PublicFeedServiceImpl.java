package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.LikeResponse;
import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicFeedServiceImpl implements PublicFeedService {

    // Repository for completed cleanup assignments
    private final CleanupAssignmentRepository assignmentRepository;

    private final PublicFeedAnalyticsService publicFeedAnalyticsService;

    // Resolves the signed-in user, who a like belongs to
    private final UserRepository userRepository;


    @Override
    public List<PublicFeedResponse> getPublicFeed() {

        // Resolved once for the whole list rather than per story
        User currentUser = currentUserOrNull();

        return assignmentRepository.findCompletedVerifiedAssignments()
                .stream()
                .map(assignment -> mapToResponse(assignment, currentUser)) // Entity → DTO
                .toList();
    }

    @Override
    public PublicFeedResponse getPublicFeedByReportId(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found for report id: " + reportId));

        return mapToResponse(assignment, currentUserOrNull());
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
     * currentUser may be null, for a visitor reading without an account.
     * In that case no like can belong to them, so likedByMe is false.
     */
    private PublicFeedResponse mapToResponse(CleanupAssignment assignment, User currentUser) {
        // Community appreciation analytics
        PublicFeedAnalytics analytics = publicFeedAnalyticsService.getAnalytics(assignment);

        // Whether this reader's own like of the cleanup stands
        boolean likedByMe = currentUser != null
                && publicFeedAnalyticsService.hasLiked(assignment, currentUser);


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