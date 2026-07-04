package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class PublicFeedAnalyticsServiceImpl implements PublicFeedAnalyticsService {

    // Analytics repository
    private final PublicFeedAnalyticsRepository analyticsRepository;

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
    public void incrementLikeCount(CleanupAssignment assignment) {

        PublicFeedAnalytics analytics = getAnalytics(assignment);

        analytics.setLikeCount(analytics.getLikeCount() + 1);

        analyticsRepository.save(analytics);
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