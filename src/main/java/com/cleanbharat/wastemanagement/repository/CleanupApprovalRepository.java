package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupApproval;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CleanupApprovalRepository extends JpaRepository<CleanupApproval, Long> {

    // Full municipal decision trail for one assignment, oldest first
    List<CleanupApproval> findByAssignmentOrderByDecidedAtAsc(CleanupAssignment assignment);

    // Guard: has this assignment already received an APPROVED decision at the given stage?
    boolean existsByAssignmentAndStageAndDecision(CleanupAssignment assignment,
                                                  ApprovalStage stage,
                                                  ApprovalDecision decision);

    // Latest matching decision - used to read who authorized the cleaner and when
    Optional<CleanupApproval> findFirstByAssignmentAndStageAndDecisionOrderByDecidedAtDesc(CleanupAssignment assignment,
                                                                                          ApprovalStage stage,
                                                                                          ApprovalDecision decision);

    // Deletion guard: an officer who has taken decisions cannot be wiped silently
    boolean existsByDecidedBy(User decidedBy);
}