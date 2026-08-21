package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.ProposalStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ============================================================================
 * CleanupProposal (Phase 14 - municipal authorized cleanup)
 * ============================================================================
 *
 * A cleaner's formal offer to clean a reported site.
 *
 * Replaces the old direct-claim behavior: submitting a proposal does NOT
 * hand the assignment over. Several cleaners may propose for the same site
 * and the municipal corporation later picks one, so this table holds the
 * evidence and the plan a municipal officer needs to make that choice.
 *
 * The inspection evidence (coordinates, timestamp, optional photograph) is
 * kept here rather than in a separate table because a proposal is always
 * the outcome of exactly one site visit - a 1:1 relationship needs no join.
 *
 * Unique on (assignment_id, cleaner_id): one live proposal per cleaner per
 * assignment. A revision updates the existing row instead of stacking
 * duplicates, which keeps the municipal review list readable.
 * ============================================================================
 */
@Entity
@Table(
        name = "cleanup_proposals",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_proposal_assignment_cleaner",
                columnNames = {"assignment_id", "cleaner_id"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The cleanup assignment this proposal is for.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private CleanupAssignment assignment;

    /**
     * The cleaner who inspected the site and is offering to clean it.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cleaner_id", nullable = false)
    private User cleaner;

    // ---------------------------------------------------------------
    // Inspection evidence - proves the cleaner actually visited
    // ---------------------------------------------------------------

    /**
     * Optional photograph taken during the inspection (Cloudinary URL).
     *
     * Optional because a cleaner may inspect a site where photography is
     * impractical; the coordinates below are the mandatory part.
     */
    @Column(name = "inspection_image_url")
    private String inspectionImageUrl;

    /**
     * Where the cleaner stood while inspecting. Mandatory: this is what the
     * 50 m proximity rule is checked against.
     */
    @Column(name = "inspection_latitude", nullable = false)
    private Double inspectionLatitude;

    @Column(name = "inspection_longitude", nullable = false)
    private Double inspectionLongitude;

    /**
     * Measured distance from the citizen's reported coordinates, stored so a
     * municipal officer can see how close the inspection actually was
     * without recomputing it.
     */
    @Column(name = "inspection_distance_meters")
    private Double inspectionDistanceMeters;

    /**
     * When the inspection was captured (server time at submission).
     */
    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt;

    /**
     * What the cleaner saw on site - waste type, spread, access problems.
     */
    @Column(name = "site_observations", nullable = false, length = 1000)
    private String siteObservations;

    // ---------------------------------------------------------------
    // Execution plan - what the municipality is approving
    // ---------------------------------------------------------------

    /**
     * Expected working days. Days rather than hours: multi-day clearing of
     * large dumps is the normal case for this platform.
     */
    @Column(name = "estimated_duration_days", nullable = false)
    private Integer estimatedDurationDays;

    /**
     * How many workers the cleaner will deploy.
     */
    @Column(name = "manpower_count", nullable = false)
    private Integer manpowerCount;

    /**
     * Tools and vehicles to be used.
     */
    @Column(name = "equipment", nullable = false, length = 500)
    private String equipment;

    /**
     * How the site will be cleaned (manual pick-up, machine loading, etc).
     */
    @Column(name = "cleaning_method", nullable = false, length = 500)
    private String cleaningMethod;

    /**
     * Where the collected waste goes and how it is segregated.
     *
     * Kept as a field instead of a separate disposal-record entity: it has
     * no lifecycle of its own, it is simply part of the approved plan.
     */
    @Column(name = "waste_handling_plan", nullable = false, length = 1000)
    private String wasteHandlingPlan;

    /**
     * Rough waste volume, free text ("about 3 tractor loads"). Optional
     * because an accurate figure is rarely known before work starts.
     */
    @Column(name = "estimated_waste_volume", length = 200)
    private String estimatedWasteVolume;

    /**
     * Date the cleaner intends to begin, if approved.
     */
    @Column(name = "proposed_start_date")
    private LocalDate proposedStartDate;

    /**
     * Anything else the officer should know. Optional.
     */
    @Column(name = "remarks", length = 1000)
    private String remarks;

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    /**
     * SUBMITTED until a municipal officer decides (Task 3), or WITHDRAWN if
     * the cleaner pulls it back.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProposalStatus status;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    /**
     * Touched on every revision, so an officer can tell a freshly edited
     * proposal from an untouched one.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Defaults applied on insert so callers never have to set them.
     */
    @PrePersist
    protected void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (submittedAt == null) {
            submittedAt = now;
        }

        // Inspection time falls back to submission time when not supplied
        if (inspectedAt == null) {
            inspectedAt = now;
        }

        if (status == null) {
            status = ProposalStatus.SUBMITTED;
        }
    }

    /**
     * Records the moment of the last edit.
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}