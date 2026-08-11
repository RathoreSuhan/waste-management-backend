package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicFeedAnalyticsServiceImpl implements PublicFeedAnalyticsService {

    // Analytics repository
    private final PublicFeedAnalyticsRepository analyticsRepository;

    /*
      A person's engagement with a report lives in one row of the votes
      table, holding their urgency rating and whether they appreciated
      the cleanup. The like is kept there because the report already
      identifies its cleanup, and because that table's key - one row per
      person per report - is what makes a second like impossible.
     */
    private final VoteRepository voteRepository;


    @Override
    public void initializeAnalytics(CleanupAssignment assignment) {

        // Prevent duplicate analytics creation
        if (analyticsRepository.existsByCleanupAssignment(assignment)) {
            return;
        }

        PublicFeedAnalytics analytics = PublicFeedAnalytics.builder()
                .cleanupAssignment(assignment)
                .build();

        analyticsRepository.save(analytics);
    }

    @Override
    public void incrementViewCount(CleanupAssignment assignment) {

        PublicFeedAnalytics analytics = getAnalytics(assignment);

        analytics.setViewCount(analytics.getViewCount() + 1);

        analyticsRepository.save(analytics);
    }

    @Override
    public boolean toggleLike(CleanupAssignment assignment, User user) {

        // The cleanup is reached through its report, so the report is
        // what a like is recorded against
        GarbageReport report = assignment.getReport();

        // This user's existing engagement with the report, if any
        Vote vote = voteRepository
                .findByUserAndReport(user, report)
                .orElse(null);

        // Whether the like stands once this call is finished
        boolean liked;

        if (vote != null && Boolean.TRUE.equals(vote.getLiked())) {

            // Second press withdraws the like rather than adding another
            vote.setLiked(null);
            liked = false;

            /*
             * If this row was only ever a like, it now records nothing
             * and is removed. If it also holds a rating, that rating is
             * about the garbage and must survive the like being taken
             * back, so the row stays.
             */
            if (vote.getRating() == null) {
                voteRepository.delete(vote);
            } else {
                voteRepository.save(vote);
            }

        } else if (vote != null) {

            // This user has already rated the report; the like joins
            // that same row rather than making a second one
            vote.setLiked(true);
            voteRepository.save(vote);
            liked = true;

        } else {

            voteRepository.save(
                    Vote.builder()
                            .user(user)
                            .report(report)
                            .liked(true)
                            .build()
            );
            liked = true;
        }

        /*
         * Recount rather than adjust by one.
         *
         * The stored total is a summary of the like records, so counting
         * them is what keeps the two agreeing. Adding or subtracting one
         * would carry forward any discrepancy already present - such as
         * the inflated totals from before a like was tied to a user.
         */
        PublicFeedAnalytics analytics = getAnalytics(assignment);

        analytics.setLikeCount(
                voteRepository.countByReportAndLikedTrue(report)
        );

        analyticsRepository.save(analytics);

        return liked;
    }

    @Override
    public boolean hasLiked(CleanupAssignment assignment, User user) {
        return voteRepository.existsByUserAndReportAndLikedTrue(
                user, assignment.getReport()
        );
    }


    @Override
    public void incrementShareCount(CleanupAssignment assignment) {

        PublicFeedAnalytics analytics = getAnalytics(assignment);

        analytics.setShareCount(analytics.getShareCount() + 1);

        analyticsRepository.save(analytics);
    }

    @Override
    public PublicFeedAnalytics getAnalytics(CleanupAssignment assignment) {
        return analyticsRepository
                .findByCleanupAssignment(assignment)
                .orElseThrow(() -> new ResourceNotFoundException("Public feed analytics not found."));
    }
}