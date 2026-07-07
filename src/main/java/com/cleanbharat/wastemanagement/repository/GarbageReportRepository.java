package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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


    /**
     * Counts reports having a specific status.
     */
    long countByStatus(ReportStatus status);


    /**
     * Number of reports created by a user.
     */
    long countByUser(User user);


    /**
     * Searches reports by title, city, state or pincode.
     */
    @Query("""
        SELECT r
        FROM GarbageReport r
        WHERE
              LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.state) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.pincode) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY r.createdAt DESC
        """)
    List<GarbageReport> searchReports(@Param("keyword") String keyword);


    /**
     * Filters reports by optional status, city and state.
     */
    @Query("""
        SELECT r
        FROM GarbageReport r
        WHERE
            (CAST(:status AS string) IS NULL OR r.status = :status)
        AND
            (COALESCE(:city,'')='' OR LOWER(r.city)=LOWER(:city))
        AND
            (COALESCE(:state,'')='' OR LOWER(r.state)=LOWER(:state))
        ORDER BY r.createdAt DESC
        """)
    List<GarbageReport> filterReports(
            @Param("status") ReportStatus status,
            @Param("city") String city,
            @Param("state") String state
    );
}