package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;

public interface PublicFeedAnalyticsService {

    // Create analytics after successful cleanup
    void initializeAnalytics(CleanupAssignment assignment);

    // Increase view count
    void incrementViewCount(CleanupAssignment assignment);

    // Increase like count
    void incrementLikeCount(CleanupAssignment assignment);

    // Increase share count
    void incrementShareCount(CleanupAssignment assignment);

    // Fetch analytics of a completed cleanup
    PublicFeedAnalytics getAnalytics(CleanupAssignment assignment);
}