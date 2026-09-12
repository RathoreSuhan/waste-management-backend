package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.PublicFeedAnalytics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PublicFeedAnalyticsRepository extends JpaRepository<PublicFeedAnalytics, Long> {

    // Find analytics of a completed cleanup
    Optional<PublicFeedAnalytics> findByCleanupAssignment(CleanupAssignment cleanupAssignment);

    // Check whether analytics already exists
    boolean existsByCleanupAssignment(CleanupAssignment cleanupAssignment);


    /**
     * Analytics for several cleanups at once.
     *
     * getAnalytics() resolves one cleanup's counters with one query. Called
     * from the feed mapper that becomes a query per story - ten of them for
     * a page of ten - for data that a single IN query can return in one go.
     *
     * The caller runs this once per page and looks each row up from the
     * result, rather than the mapper asking for its own.
     */
    List<PublicFeedAnalytics> findByCleanupAssignmentIn(List<CleanupAssignment> cleanupAssignments);
}