package com.cleanbharat.wastemanagement.enums;

public enum AssignmentStatus {

    // Created automatically when report is created (open for cleaner proposals)
    PENDING,

    // At least one cleaner submitted a cleanup proposal, municipality review pending
    PROPOSAL_SUBMITTED,

    // Municipality approved a proposal and authorized that cleaner
    ASSIGNED,

    // Approved cleaner started cleaning
    IN_PROGRESS,

    // Cleanup evidence submitted (GPS + AI checked), municipal final approval pending
    AWAITING_APPROVAL,

    /**
     * Municipality reviewed the submitted evidence and sent the work back.
     *
     * The cleaner keeps the assignment and simply continues cleaning, then
     * re-submits proof, which runs GPS + AI again and returns the site to
     * AWAITING_APPROVAL for a fresh municipal review. This is deliberately a
     * distinct state from IN_PROGRESS so the cleaner, the officer and the
     * audit trail can all tell a first attempt apart from a re-do.
     */
    REWORK_REQUIRED,

    // Legacy status kept only for old rows created by the removed direct-claim flow
    CLAIMED,

    // Municipality gave final approval, reward + public feed released
    COMPLETED
}
