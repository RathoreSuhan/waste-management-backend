package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.CleanupProposal;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * ============================================================================
 * CleanupProposalRepository (Phase 14)
 * ============================================================================
 *
 * Queries for the proposal stage of the municipal authorized cleanup flow.
 *
 * Everything here is derived-query only - no JPQL is needed because the
 * lookups are all single-table with simple relation filters.
 * ============================================================================
 */
public interface CleanupProposalRepository extends JpaRepository<CleanupProposal, Long> {

    /**
     * A cleaner's own proposals, newest first (My Proposals screen).
     */
    List<CleanupProposal> findByCleanerOrderBySubmittedAtDesc(User cleaner);

    /**
     * Every proposal for one assignment - used by the municipal review
     * screen in Task 3 and to count competing offers today.
     */
    List<CleanupProposal> findByAssignmentOrderBySubmittedAtAsc(CleanupAssignment assignment);

    /**
     * The caller's existing proposal for an assignment, if any.
     *
     * Backs both the duplicate check and the revision path, since a cleaner
     * is allowed only one row per assignment.
     */
    Optional<CleanupProposal> findByAssignmentAndCleaner(CleanupAssignment assignment, User cleaner);

    /**
     * How many cleaners have offered for this site.
     */
    long countByAssignment(CleanupAssignment assignment);

    /**
     * Live (non-withdrawn) proposals in a given state - used to decide
     * whether an assignment still has offers awaiting review.
     */
    long countByAssignmentAndStatus(CleanupAssignment assignment, ProposalStatus status);

    /**
     * Guards cleaner deletion: a cleaner with proposals on record must not
     * silently disappear from an officer's review queue.
     */
    boolean existsByCleaner(User cleaner);

    /**
     * All proposals belonging to one assignment, needed when an assignment
     * or report is deleted so the Cloudinary inspection images can be
     * removed before the rows cascade away.
     */
    List<CleanupProposal> findByAssignment(CleanupAssignment assignment);

    /**
     * All proposals belonging to one cleaner, used by user deletion for the
     * same image cleanup reason.
     */
    List<CleanupProposal> findByCleaner(User cleaner);

    /**
     * Just the inspection photo links of one assignment's proposals.
     *
     * Deletion only needs the URLs, not whole entities with their cleaner and
     * assignment graphs, so this projection keeps the cascade light even when a
     * popular site collected many competing offers. Blank and missing links are
     * filtered out here so the caller never has to check again.
     */
    @Query("""
            select p.inspectionImageUrl
            from CleanupProposal p
            where p.assignment = :assignment
              and p.inspectionImageUrl is not null
              and p.inspectionImageUrl <> ''
            """)
    List<String> findInspectionImageUrlsByAssignment(@Param("assignment") CleanupAssignment assignment);

    /**
     * Removes every proposal of one assignment in a single statement.
     *
     * The assignment does cascade to its proposals through the entity mapping,
     * but that path loads and deletes each row one by one and only runs when the
     * assignment itself is removed. Deleting them explicitly, right after the
     * approvals that reference them, keeps the order predictable and the report
     * deletion cheap.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CleanupProposal p where p.assignment = :assignment")
    int deleteByAssignment(@Param("assignment") CleanupAssignment assignment);
}
