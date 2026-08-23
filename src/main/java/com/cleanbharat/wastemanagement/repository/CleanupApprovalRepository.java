package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupApproval;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Newest decision recorded against one proposal at the given stage.
     *
     * Drives the review lock: while this reads REVISION_REQUIRED the officer is
     * waiting for the cleaner, and once the cleaner resubmits the row that
     * follows reads REVISION_SUBMITTED and the buttons open again. The id is the
     * tie-breaker because two rows can share the same decidedAt second.
     */
    Optional<CleanupApproval> findFirstByProposalAndStageOrderByDecidedAtDescIdDesc(CleanupProposal proposal,
                                                                                   ApprovalStage stage);

    // Deletion guard: an officer who has taken decisions cannot be wiped silently
    boolean existsByDecidedBy(User decidedBy);

    /**
     * Clears the whole decision trail of one assignment in a single statement.
     *
     * cleanup_approvals points at both cleanup_assignments and cleanup_proposals,
     * so these rows must go before either parent is removed, otherwise PostgreSQL
     * refuses the delete with a foreign key violation (SQLSTATE 23503) and the
     * admin only sees a generic "conflicts with existing records" message.
     *
     * A bulk delete is used instead of the derived deleteByAssignment so a long
     * review history costs one round trip rather than one per row. The flush and
     * clear keep the persistence context honest for the deletes that follow.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CleanupApproval a where a.assignment = :assignment")
    int deleteByAssignment(@Param("assignment") CleanupAssignment assignment);
}
