package com.cleanbharat.wastemanagement.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
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