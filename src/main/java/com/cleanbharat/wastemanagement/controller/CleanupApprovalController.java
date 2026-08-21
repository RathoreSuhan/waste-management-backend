package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ApprovalDecisionRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.dto.CleanupApprovalResponse;
import com.cleanbharat.wastemanagement.dto.CleanupAssignmentResponse;
import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalDashboardStatsResponse;
import com.cleanbharat.wastemanagement.service.CleanupApprovalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Municipal officer endpoints for authorizing cleaners and signing off completed work.
 * Every method is jurisdiction-scoped inside the service layer.
 */
@RestController
@RequestMapping("/api/cleanup-approvals")
@RequiredArgsConstructor
public class CleanupApprovalController {

    private final CleanupApprovalService cleanupApprovalService;

    // Overview counters for the officer's own Municipal Corporation
    @GetMapping("/stats")
    public ResponseEntity<MunicipalDashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(cleanupApprovalService.getDashboardStats());
    }

    // Assignments with at least one proposal waiting for a municipal decision
    @GetMapping("/proposal-queue")
    public ResponseEntity<List<CleanupAssignmentResponse>> getProposalQueue() {
        return ResponseEntity.ok(cleanupApprovalService.getPendingReviewAssignments());
    }

    // All competing proposals submitted for one assignment
    @GetMapping("/assignment/{assignmentId}/proposals")
    public ResponseEntity<List<CleanupProposalResponse>> getProposalsForAssignment(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(cleanupApprovalService.getProposalsForAssignment(assignmentId));
    }

    // Approve & assign, reject, or request a revision on a single proposal
    @PostMapping("/proposal/{proposalId}")
    public ResponseEntity<CleanupApprovalResponse> decideProposal(
            @PathVariable Long proposalId,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ResponseEntity.ok(cleanupApprovalService.decideProposal(proposalId, request));
    }

    // Cleanups whose proof is AI-verified and awaiting final municipal sign-off
    @GetMapping("/completion-queue")
    public ResponseEntity<List<CleanupAssignmentResponse>> getCompletionQueue() {
        return ResponseEntity.ok(cleanupApprovalService.getPendingCompletionAssignments());
    }

    // Final sign-off: approval releases the reward and resolves the report
    @PostMapping("/completion/{assignmentId}")
    public ResponseEntity<CleanupApprovalResponse> decideCompletion(
            @PathVariable Long assignmentId,
            @Valid @RequestBody ApprovalDecisionRequest request
    ) {
        return ResponseEntity.ok(cleanupApprovalService.decideCompletion(assignmentId, request));
    }

    // Full audit trail of every municipal decision taken on an assignment
    @GetMapping("/assignment/{assignmentId}/history")
    public ResponseEntity<List<CleanupApprovalResponse>> getApprovalHistory(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(cleanupApprovalService.getApprovalHistory(assignmentId));
    }

    // Awarded work currently being executed, including jobs sent back for rework
    @GetMapping("/active-cleanups")
    public ResponseEntity<List<CleanupAssignmentResponse>> getActiveCleanups() {
        return ResponseEntity.ok(cleanupApprovalService.getActiveCleanups());
    }

    // One assignment in full detail for the review screens
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<CleanupAssignmentResponse> getAssignmentForReview(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(cleanupApprovalService.getAssignmentForReview(assignmentId));
    }

    // Read-only copy of the cleaner's on-site diary, used as supporting evidence
    @GetMapping("/assignment/{assignmentId}/activity-logs")
    public ResponseEntity<List<CleanupActivityLogResponse>> getAssignmentActivityLogs(@PathVariable Long assignmentId) {
        return ResponseEntity.ok(cleanupApprovalService.getAssignmentActivityLogs(assignmentId));
    }
}
