package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.DashboardAnalyticsResponse;
import com.cleanbharat.wastemanagement.dto.ReportAnalyticsResponse;
import com.cleanbharat.wastemanagement.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    // Get analytics of a specific report
    @GetMapping("/report/{reportId}")
    public ResponseEntity<ReportAnalyticsResponse> getReportAnalytics(@PathVariable Long reportId) {
        ReportAnalyticsResponse response = analyticsService.getReportAnalytics(reportId);
        return ResponseEntity.ok(response);
    }

    //Get analytics of the Trending topics
    @GetMapping("/trending")
    public ResponseEntity<List<ReportAnalyticsResponse>> getTrendingReports() {
        return ResponseEntity.ok(analyticsService.getTrendingReports());
    }

    // Get dashboard analytics of the entire system
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardAnalyticsResponse> getDashboardAnalytics() {
        DashboardAnalyticsResponse response = analyticsService.getDashboardAnalytics();
        return ResponseEntity.ok(response);
    }
}