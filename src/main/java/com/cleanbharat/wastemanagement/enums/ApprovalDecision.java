package com.cleanbharat.wastemanagement.enums;

public enum ApprovalDecision {

    // Municipality accepted the proposal / cleanup evidence
    APPROVED,

    // Municipality rejected it outright
    REJECTED,

    // Municipality wants changes (proposal edit or cleanup rework)
    REVISION_REQUIRED,

    // Cleaner has returned the corrected plan, so the review buttons unlock again
    // Recorded by the system on resubmission, never chosen by a municipal officer
    REVISION_SUBMITTED
}
