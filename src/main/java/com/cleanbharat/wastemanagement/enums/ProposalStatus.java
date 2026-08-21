package com.cleanbharat.wastemanagement.enums;

public enum ProposalStatus {

    // Cleaner submitted the proposal, waiting for municipal review
    SUBMITTED,

    // Municipality approved this proposal and authorized its cleaner
    APPROVED,

    // Municipality rejected this proposal
    REJECTED,

    // Municipality asked the cleaner to correct and resubmit the plan
    REVISION_REQUIRED,

    // Cleaner pulled back their own proposal
    WITHDRAWN
}
