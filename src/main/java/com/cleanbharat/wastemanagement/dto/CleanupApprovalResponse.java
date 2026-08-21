package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import com.cleanbharat.wastemanagement.enums.ApprovalStage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Flattened view of a single municipal decision.
 * Entities are never returned directly, so lazy associations stay inside the service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupApprovalResponse {

    private Long approvalId;                  // Identifier of the decision record itself
    private Long assignmentId;                // Cleanup assignment it belongs to
    private Long proposalId;                  // Proposal decided on (null for COMPLETION stage)
    private Long reportId;                    // Originating garbage report
    private String reportTitle;                // Report title, so the UI needs no extra call
    private ApprovalStage stage;              // PROPOSAL or COMPLETION
    private ApprovalDecision decision;        // APPROVED / REJECTED / REVISION_REQUIRED
    private String remarks;                    // Officer's note to the cleaner
    private String decidedByName;              // Officer name (no email/ID leaked)
    private String municipalCorporationName;   // Authority that took the decision
    private LocalDateTime decidedAt;           // When it was decided
}