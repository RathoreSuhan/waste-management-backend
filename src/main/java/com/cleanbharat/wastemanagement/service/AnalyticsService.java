package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.DashboardAnalyticsResponse;
import com.cleanbharat.wastemanagement.dto.ReportAnalyticsResponse;
import java.util.List;

public interface AnalyticsService {

    // Analytics of a specific report
    ReportAnalyticsResponse getReportAnalytics(Long reportId);

    // Recalculate and save engagement score
    void recalculateEngagementScore(Long reportId);

    // Get all trending reports
    List<ReportAnalyticsResponse> getTrendingReports();

    // Dashboard analytics of the whole system
    DashboardAnalyticsResponse getDashboardAnalytics();
}