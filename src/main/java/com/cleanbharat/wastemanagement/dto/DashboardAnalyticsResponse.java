package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor // Required by Jackson to rebuild this DTO on a Redis cache HIT
@Builder
public class DashboardAnalyticsResponse {

    // Total garbage reports
    private Long totalReports;

    // Total citizen votes
    private Long totalVotes;

    // Total comments (top-level)
    private Long totalComments;

    // Total replies
    private Long totalReplies;

    // Average urgency score of all reports
    private Double averageUrgencyScore;

    // Average engagement score of all reports
    private Double averageEngagementScore;

    // Report having highest engagement score
    private Long mostTrendingReportId;
}