package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.ApprovalDecision;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body a municipal officer sends when deciding on a proposal or on submitted cleanup evidence.
 * Note: the approval STAGE is deliberately NOT part of this body - it is derived from the
 * endpoint that was called, so a caller can never make a COMPLETION call behave like a
 * PROPOSAL call (or vice versa).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalDecisionRequest {

    // APPROVED / REJECTED / REVISION_REQUIRED - mandatory verdict
    @NotNull(message = "Decision is required")
    private ApprovalDecision decision;

    // Optional note shown to the cleaner (reason for rejection, what to revise, etc.)
    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    private String remarks;
}