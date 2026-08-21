package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * CleanupProposalResponse (Phase 14)
 * ============================================================================
 *
 * One proposal, flattened for the cleaner's screens.
 *
 * Carries a little of the assignment and report with it so the My Proposals
 * list is renderable without a second call per row.
 * ============================================================================
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupProposalResponse {

    private Long proposalId;

    // Context - which site this offer is for
    private Long assignmentId;
    private Long reportId;
    private String reportTitle;
    private String address;
    private String city;
    private String assignmentStatus;

    // Who is offering. The municipal officer compares competing cleaners, so the
    // review payload also carries the cleaner's category and organization name.
    private Long cleanerId;
    private String cleanerName;
    private String cleanerType;         // INDIVIDUAL / NGO / PRIVATE_COMPANY etc.
    private String cleanerOrganization; // filled only for organization cleaners

    // Inspection evidence
    private String inspectionImageUrl;
    private Double inspectionLatitude;
    private Double inspectionLongitude;
    private Double inspectionDistanceMeters;
    private LocalDateTime inspectedAt;
    private String siteObservations;

    // Execution plan
    private Integer estimatedDurationDays;
    private Integer manpowerCount;
    private String equipment;
    private String cleaningMethod;
    private String wasteHandlingPlan;
    private String estimatedWasteVolume;
    private LocalDate proposedStartDate;
    private String remarks;

    // Lifecycle
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;

    /**
     * How many cleaners are competing for the same site. Shown so a cleaner
     * understands that submitting does not reserve the work.
     */
    private Long totalProposalsForAssignment;
}
