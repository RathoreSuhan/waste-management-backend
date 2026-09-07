package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.DashboardAnalyticsResponse;
import com.cleanbharat.wastemanagement.dto.ReportAnalyticsResponse;
import com.cleanbharat.wastemanagement.entity.Comment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final GarbageReportRepository reportRepository;
    private final CommentRepository commentRepository;
    private final VoteRepository voteRepository;

    @Override
    public void recalculateEngagementScore(Long reportId) {

        GarbageReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        List<Comment> comments = commentRepository.findByReportId(reportId);

        long commentCount = countComments(comments);

        long replyCount = countReplies(comments);

        double discussionScore = (commentCount * 2.0) + (replyCount * 1.0);

        double urgencyScore = report.getUrgencyScore() == null ? 0.0 : report.getUrgencyScore();

        double engagementScore = urgencyScore + discussionScore;

        report.setEngagementScore(engagementScore);

        reportRepository.save(report);
    }

    @Override
    public ReportAnalyticsResponse getReportAnalytics(Long reportId) {

        // Find report
        GarbageReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        // Get all comments and replies of report
        List<Comment> comments = commentRepository.findByReportId(reportId);

        // Count only top-level comments
        long commentCount = countComments(comments);

        // Count all replies (nested included)
        long replyCount = countReplies(comments);

        // Total discussion
        long discussionCount = commentCount + replyCount;

        // Discussion score formula
        double discussionScore = (commentCount * 2.0) + (replyCount * 1.0);

        // Handle null urgency score
        double urgencyScore = report.getUrgencyScore() == null ? 0.0 : report.getUrgencyScore();

        // Final engagement score
        double engagementScore = urgencyScore + discussionScore;

        return ReportAnalyticsResponse.builder()
                .reportId(report.getId())
                .urgencyScore(urgencyScore)
                .commentCount(commentCount)
                .replyCount(replyCount)
                .discussionCount(discussionCount)
                .engagementScore(engagementScore)
                .build();
    }

    @Override
    public List<ReportAnalyticsResponse> getTrendingReports() {

        // Fetch all reports sorted by engagement score
        List<GarbageReport> reports =
                reportRepository.findAllByOrderByEngagementScoreDesc();

        // Fetch ALL comments only once
        List<Comment> allComments = commentRepository.findAll();

        // Group comments by reportId
        Map<Long, List<Comment>> commentsByReport =
                allComments.stream()
                        .collect(Collectors.groupingBy(
                                comment -> comment.getReport().getId()
                        ));

        return reports.stream()
                .map(report -> {

                    List<Comment> comments = commentsByReport.getOrDefault(report.getId(), List.of());

                    long commentCount = countComments(comments);

                    long replyCount = countReplies(comments);

                    long discussionCount = commentCount + replyCount;

                    return ReportAnalyticsResponse.builder()
                            .reportId(report.getId())
                            .urgencyScore(report.getUrgencyScore())
                            .commentCount(commentCount)
                            .replyCount(replyCount)
                            .discussionCount(discussionCount)
                            .engagementScore(report.getEngagementScore())
                            .build();

                })
                .toList();
    }

    /*
     * @Cacheable(value = "homepage_impact_stats", key = "'metrics'")
     *
     * WHAT THIS DOES:
     *   Tells Spring: "Before executing this method, check Redis cache
     *   named 'homepage_impact_stats' for an entry with key 'metrics'.
     *
     * CACHE HIT:
     *   Redis has a value → return it immediately (~2ms response).
     *   Method body NEVER executes. Neon PostgreSQL is NOT touched.
     *
     * CACHE MISS (first call or after eviction):
     *   Redis has no value → execute method body (6+ SQL COUNT/SUM/AVG queries),
     *   store the result in Redis under key 'metrics', then return it (~250ms).
     *
     * NEXT CALL: Redis HIT kicks in for ALL callers until:
     *   a) TTL expires (10 minutes — safety net)
     *   b) @CacheEvict is triggered by a data-changing operation
     *
     * WHY THIS ENDPOINT:
     *   This method runs 6+ heavy SQL aggregations:
     *     - reportRepository.count() → COUNT(*) on all reports
     *     - voteRepository.countByRatingIsNotNull() → COUNT with WHERE clause
     *     - commentRepository.countTopLevelComments() → COUNT with filter
     *     - commentRepository.countReplies() → COUNT with filter
     *     - reportRepository.getAverageUrgencyScore() → AVG aggregation
     *     - reportRepository.getAverageEngagementScore() → AVG aggregation
     *     - reportRepository.findTopByOrderByEngagementScoreDesc() → ORDER BY LIMIT 1
     *
     *   These queries hit multiple tables every single request from the
     *   homepage, public leaderboard, and external dashboards.
     *   Caching reduces load on Neon PostgreSQL by ~99% for this endpoint.
     *
     * WHY @Cacheable INSTEAD OF MANUAL CACHING:
     *   Spring handles the entire cache-aside pattern automatically:
     *     - Check Redis before execution
     *     - Execute only on MISS
     *     - Serialize result to JSON
     *     - Store in Redis with TTL
     *     - Return result
     *   No boilerplate RedisTemplate.get/set code needed.
     * ============================================================
     */
    @Cacheable(value = "homepage_impact_stats", key = "'metrics'")
    @Override
    public DashboardAnalyticsResponse getDashboardAnalytics() {

        // Total reports in the system
        long totalReports = reportRepository.count();

        // Total votes submitted
        // Counts ratings only: a row that merely records a like of a
        // cleanup is not a vote and would overstate this figure.
        long totalVotes = voteRepository.countByRatingIsNotNull();

        // Total top-level comments
        long totalComments = commentRepository.countTopLevelComments();

        // Total replies
        long totalReplies = commentRepository.countReplies();

        // Average urgency score
        Double averageUrgencyScore = reportRepository.getAverageUrgencyScore();

        // Average engagement score
        Double averageEngagementScore = reportRepository.getAverageEngagementScore();

        // Highest engagement report
        GarbageReport trendingReport = reportRepository.findTopByOrderByEngagementScoreDesc();

        return DashboardAnalyticsResponse.builder()
                .totalReports(totalReports)
                .totalVotes(totalVotes)
                .totalComments(totalComments)
                .totalReplies(totalReplies)
                .averageUrgencyScore(averageUrgencyScore == null ? 0.0 : averageUrgencyScore)
                .averageEngagementScore(averageEngagementScore == null ? 0.0 : averageEngagementScore)
                .mostTrendingReportId(trendingReport == null ? null : trendingReport.getId())
                .build();
    }


    /**
     * Count only root comments
     */
    private long countComments(List<Comment> comments) {
        return comments.stream()
                .filter(comment -> comment.getParentComment() == null)
                .count();
    }

    /**
     * Count all replies including nested replies
     */
    private long countReplies(List<Comment> comments) {
        return comments.stream()
                .filter(comment -> comment.getParentComment() != null)
                .count();
    }
}