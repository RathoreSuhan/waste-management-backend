package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CleanupProposalResponse;
import com.cleanbharat.wastemanagement.dto.CreateProposalRequest;
import com.cleanbharat.wastemanagement.service.CleanupProposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
 * Cleaner-facing endpoints of the cleanup proposal workflow.
 *
 * Access is restricted to ROLE_CLEANER in SecurityConfig; every handler works
 * only on data owned by the logged-in cleaner.
 */
@RestController
@RequestMapping("/api/cleanup-proposals")
@RequiredArgsConstructor
public class CleanupProposalController {

    private final CleanupProposalService proposalService;

    // Submit an inspection-backed proposal for an open cleanup site (multipart: optional evidence image)
    @PostMapping(value = "/assignment/{assignmentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CleanupProposalResponse> submitProposal(
            @PathVariable Long assignmentId,
            @Valid @ModelAttribute CreateProposalRequest request) {

        return ResponseEntity.ok(proposalService.submitProposal(assignmentId, request));
    }

    // Revise an own proposal that is still pending review or was sent back for changes
    @PutMapping(value = "/{proposalId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CleanupProposalResponse> updateProposal(
            @PathVariable Long proposalId,
            @Valid @ModelAttribute CreateProposalRequest request) {

        return ResponseEntity.ok(proposalService.updateProposal(proposalId, request));
    }

    // Withdraw an own proposal (status change, the record is kept for audit)
    @DeleteMapping("/{proposalId}")
    public ResponseEntity<CleanupProposalResponse> withdrawProposal(@PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.withdrawProposal(proposalId));
    }

    // Proposal history of the logged-in cleaner
    @GetMapping("/my")
    public ResponseEntity<List<CleanupProposalResponse>> getMyProposals() {
        return ResponseEntity.ok(proposalService.getMyProposals());
    }

    // Single own proposal, used by the edit screen
    @GetMapping("/{proposalId}")
    public ResponseEntity<CleanupProposalResponse> getProposal(@PathVariable Long proposalId) {
        return ResponseEntity.ok(proposalService.getProposal(proposalId));
    }
}
