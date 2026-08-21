package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.ApprovalDecisionRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.dto.CleanupApprovalResponse;
import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalDashboardStatsResponse;

import java.util.List;

/**
 * Municipal approval workflow.
 * Every method is scoped to the logged-in officer's own Municipal Corporation,
 * so one city can never act on another city's reports.
 */
public interface CleanupApprovalService {

    // Sites in this corporation that have proposals waiting for a decision
    List<CleanupAssignmentResponse> getPendingReviewAssignments();

    // Competing proposals for one site, so the officer can compare before awarding
    List<CleanupProposalResponse> getProposalsForAssignment(Long assignmentId);

    // Approve & assign / reject / request revision on a single proposal
    CleanupApprovalResponse decideProposal(Long proposalId, ApprovalDecisionRequest request);

    // Cleanups whose evidence has been submitted and awaits final municipal sign-off
    List<CleanupAssignmentResponse> getPendingCompletionAssignments();

    // Final sign-off - the only path that marks a cleanup COMPLETED and releases the reward
    CleanupApprovalResponse decideCompletion(Long assignmentId, ApprovalDecisionRequest request);

    // Full decision trail for one assignment
    List<CleanupApprovalResponse> getApprovalHistory(Long assignmentId);

    // Dashboard counters for the officer's own corporation only
    MunicipalDashboardStatsResponse getDashboardStats();

    // Awarded work currently being executed (assigned, in progress or sent back for rework)
    List<CleanupAssignmentResponse> getActiveCleanups();

    // One assignment in full detail, for the review screens
    CleanupAssignmentResponse getAssignmentForReview(Long assignmentId);

    // Read-only view of the cleaner's on-site activity diary for that assignment
    List<CleanupActivityLogResponse> getAssignmentActivityLogs(Long assignmentId);
}
