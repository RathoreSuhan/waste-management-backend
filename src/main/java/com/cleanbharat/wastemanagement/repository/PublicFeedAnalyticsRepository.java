package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublicFeedAnalyticsRepository extends JpaRepository<PublicFeedAnalytics, Long> {

    // Find analytics of a completed cleanup
    Optional<PublicFeedAnalytics> findByCleanupAssignment(CleanupAssignment cleanupAssignment);

    // Check whether analytics already exists
    boolean existsByCleanupAssignment(CleanupAssignment cleanupAssignment);
}