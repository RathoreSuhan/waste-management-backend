package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // NEW
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GarbageReportRepository extends JpaRepository<GarbageReport, Long> {

    // Reports created by a specific user
    List<GarbageReport> findByUser(User user);

    // Trending reports sorted by engagement score descending
    List<GarbageReport> findAllByOrderByEngagementScoreDesc();

    // Report having highest engagement score
    GarbageReport findTopByOrderByEngagementScoreDesc();

    // Average urgency score of all reports
    @Query("""
            SELECT AVG(r.urgencyScore)
            FROM GarbageReport r
            """)
    Double getAverageUrgencyScore();

    // Average engagement score of all reports
    @Query("""
            SELECT AVG(r.engagementScore)
            FROM GarbageReport r
            """)
    Double getAverageEngagementScore();
}