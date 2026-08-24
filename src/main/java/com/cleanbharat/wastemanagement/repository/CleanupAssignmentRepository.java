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

    /**
     * Total completed cleanups performed by a cleaner.
     */
    long countByCleanerAndStatus(User cleaner, AssignmentStatus status
    );

    /**
     * Total AI verified cleanups performed by a cleaner.
     */
    long countByCleanerAndAiVerifiedTrue(User cleaner);

    /**
     * Counts AI verified cleanups.
     */
    long countByAiVerifiedTrue();

    /**
     * Checks whether a cleaner has any cleanup assignments.
     */
    boolean existsByCleaner(User cleaner);

    // Unawarded sites in any of the given statuses, newest first (proposal-era "available" list)
    List<CleanupAssignment> findByCleanerIsNullAndStatusInOrderByIdDesc(List<AssignmentStatus> statuses);

    // Municipal review queue: only this corporation's assignments in one status
    List<CleanupAssignment> findByAssignedMunicipalCorporationAndStatusOrderByIdDesc(
            MunicipalCorporation municipalCorporation,
            AssignmentStatus status
    );

    // Same jurisdiction scoping, but for several statuses at once
    List<CleanupAssignment> findByAssignedMunicipalCorporationAndStatusInOrderByIdDesc(
            MunicipalCorporation municipalCorporation,
            List<AssignmentStatus> statuses
    );

    /**
     * Municipal history desk: this corporation's signed-off cleanups, most
     * recently approved first.
     *
     * Ordered on completedAt rather than id, because this list is a record of
     * decisions - a long-standing report approved today belongs above a newer
     * one approved last week, which an id ordering would get backwards.
     * NULLS LAST with an id tie-break keeps a legacy COMPLETED row carrying no
     * completion timestamp at the foot of the list rather than the head of it.
     */
    @Query("""
        SELECT a
        FROM CleanupAssignment a
        WHERE a.assignedMunicipalCorporation = :municipalCorporation
          AND a.status = com.cleanbharat.wastemanagement.enums.AssignmentStatus.COMPLETED
        ORDER BY a.completedAt DESC NULLS LAST, a.id DESC
        """)
    List<CleanupAssignment> findCompletedByMunicipalCorporationNewestFirst(
            @Param("municipalCorporation") MunicipalCorporation municipalCorporation
    );

    // A cleaner's live work now spans IN_PROGRESS and REWORK_REQUIRED
    List<CleanupAssignment> findByCleanerAndStatusIn(User cleaner, List<AssignmentStatus> statuses);

    // Municipal overview tile: this corporation's assignments in one status
    long countByAssignedMunicipalCorporationAndStatus(
            MunicipalCorporation municipalCorporation,
            AssignmentStatus status
    );

    // Municipal overview tile: grouped statuses (e.g. all active cleanups)
    long countByAssignedMunicipalCorporationAndStatusIn(
            MunicipalCorporation municipalCorporation,
            List<AssignmentStatus> statuses
    );

    // Municipal overview tile: every site that falls under this corporation
    long countByAssignedMunicipalCorporation(MunicipalCorporation municipalCorporation);
}
