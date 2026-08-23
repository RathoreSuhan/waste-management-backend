package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * One reusable municipal approval record.
 * Instead of separate CleanupAuthorization and MunicipalApproval entities, this single
 * append-only entity records every municipal decision, distinguished by {@link ApprovalStage}:
 * PROPOSAL  -> municipality authorizes a cleaner before work starts
 * COMPLETION -> municipality accepts the submitted cleanup evidence after work
 * Rows are never updated; each new decision adds a new row so the trail stays intact.
 */
@Entity
@Table(name = "cleanup_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The cleanup assignment this decision applies to (always present, both stages)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private CleanupAssignment assignment;

    // The proposal being decided on - set for PROPOSAL stage, null for COMPLETION stage
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proposal_id")
    private CleanupProposal proposal;

    // Which point of the lifecycle this decision belongs to (PROPOSAL / COMPLETION)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalStage stage;

    // The verdict: APPROVED / REJECTED / REVISION_REQUIRED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    // Authority of record - the corporation that owns the report/assignment
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipal_corporation_id", nullable = false)
    private MunicipalCorporation municipalCorporation;

    /*
     * The municipal officer who personally took the decision.
     *
     * Optional on purpose: a Municipal Corporation signs in with its own
     * official account, so there is no separate officer User row to point at.
     * The corporation above is therefore the authority of record, and this
     * column only fills in if per-officer logins are introduced later.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by") // nullable: the corporation itself is the decider today
    private User decidedBy;

    // Optional justification / instructions shown to the cleaner
    @Column(length = 1000)
    private String remarks;

    // When the decision was taken
    @Column(nullable = false)
    private LocalDateTime decidedAt;

    @PrePersist
    protected void onCreate() {
        // Decisions are immutable, so the timestamp is stamped once at insert time
        if (decidedAt == null) {
            decidedAt = LocalDateTime.now();
        }
    }
}