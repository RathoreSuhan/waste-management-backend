package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.CreateProposalRequest;

import java.util.List;

/*
 * Cleanup proposal workflow (municipal-authorized model).
 *
 * A cleaner no longer claims a site directly. Instead, the cleaner inspects the
 * site and submits a proposal, which a municipal officer later approves.
 */
public interface CleanupProposalService {

    // Cleaner submits a fresh proposal for an open cleanup assignment
    CleanupProposalResponse submitProposal(Long assignmentId, CreateProposalRequest request);

    // Cleaner revises their own proposal while it is still pending review
    CleanupProposalResponse updateProposal(Long proposalId, CreateProposalRequest request);

    // Cleaner pulls their own proposal out of consideration
    CleanupProposalResponse withdrawProposal(Long proposalId);

    // All proposals submitted by the logged-in cleaner, newest first
    List<CleanupProposalResponse> getMyProposals();

    // Single proposal owned by the logged-in cleaner
    CleanupProposalResponse getProposal(Long proposalId);
}
