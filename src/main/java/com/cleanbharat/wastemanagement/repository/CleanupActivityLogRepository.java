package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupActivityLog;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/*
 * Data access for the optional cleaner activity diary (Phase 16).
 */
public interface CleanupActivityLogRepository extends JpaRepository<CleanupActivityLog, Long> {

    // Timeline for one assignment, oldest first so multi-day work reads top-down
    List<CleanupActivityLog> findByAssignmentOrderByActivityAtAsc(CleanupAssignment assignment);

    // Cheap counter surfaced on the cleaner task card ("Activity Log (3)")
    long countByAssignment(CleanupAssignment assignment);

    // Ownership-safe lookup: a cleaner may only delete their own entry
    Optional<CleanupActivityLog> findByIdAndCleaner(Long id, User cleaner);

    // Cascade helpers used by the assignment / user deletion services
    void deleteByAssignment(CleanupAssignment assignment);

    void deleteByCleaner(User cleaner);
}