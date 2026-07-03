package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface CleanupAssignmentRepository extends JpaRepository<CleanupAssignment, Long> {

    // Find assignment by report
    Optional<CleanupAssignment> findByReport(GarbageReport report);

    // All assignments of a cleaner
    List<CleanupAssignment> findByCleaner(User cleaner);

    boolean existsByReport(GarbageReport report);

    // All assignments with a specific status
    List<CleanupAssignment> findByStatus(AssignmentStatus status);

    // Cleaner's assignments filtered by status
    List<CleanupAssignment> findByCleanerAndStatus(
            User cleaner,
            AssignmentStatus status
    );

    // Assignments belonging to a Municipal Corporation
    List<CleanupAssignment> findByAssignedMunicipalCorporation(
            MunicipalCorporation municipalCorporation
    );

    // Pending assignments that have not been claimed
    List<CleanupAssignment> findByCleanerIsNullAndStatus(
            AssignmentStatus status
    );

    // Fetch completed and AI-verified cleanup assignments for Public Feed
    @Query("""
        SELECT a
        FROM CleanupAssignment a
        JOIN FETCH a.report r
        JOIN FETCH a.cleaner c
        JOIN FETCH a.assignedMunicipalCorporation mc
        WHERE a.status = com.cleanbharat.wastemanagement.enums.AssignmentStatus.COMPLETED
          AND a.aiVerified = true
        ORDER BY a.completedAt DESC
        """)
    List<CleanupAssignment> findCompletedVerifiedAssignments();


    // Fetch a completed and AI-verified cleanup assignment by report ID
    @Query("""
        SELECT a
        FROM CleanupAssignment a
        JOIN FETCH a.report r
        JOIN FETCH a.cleaner c
        JOIN FETCH a.assignedMunicipalCorporation mc
        WHERE a.status = com.cleanbharat.wastemanagement.enums.AssignmentStatus.COMPLETED
          AND a.aiVerified = true
          AND r.id = :reportId
        """)
    Optional<CleanupAssignment> findCompletedVerifiedAssignmentByReportId(
            @Param("reportId") Long reportId
    );
}