package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List; // competing cleaner proposals

@Entity
@Table(name = "cleanup_assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Garbage report assigned for cleanup
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "report_id", nullable = false)
    private GarbageReport report;

    // Cleaner who claimed this assignment
    // Null until someone claims it
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaner_id")
    private User cleaner;

    // Municipal Corporation responsible for this report
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipal_corporation_id", nullable = false)
    private MunicipalCorporation assignedMunicipalCorporation;

    // Assignment workflow status
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssignmentStatus status;

    // Uploaded after-cleanup image
    private String cleanupImageUrl;

    // When cleaner claimed the assignment
    private LocalDateTime claimedAt;

    // When cleaner started cleaning
    private LocalDateTime startedAt;

    /*
     * Start-of-work location evidence (Phase 16).
     *
     * Captured on the cleaner's device when START CLEANUP is pressed, so the
     * municipality can later see the cleaner was physically at the site before
     * work began. Nullable because legacy rows were started without it.
     */
    private Double startLatitude;

    private Double startLongitude;

    // Distance in metres between the start position and the reported location
    private Double startDistanceMeters;

    // When cleaner completed cleaning
    private LocalDateTime completedAt;

    private Boolean aiVerified;

    private Double aiConfidence;

    @Column(length = 1000)
    private String aiRemarks;

    @PrePersist
    public void prePersist() {

        if (status == null) {
            status = AssignmentStatus.PENDING;
        }
    }

    /*
     * Proposals submitted by cleaners for this site.
     *
     * Cascade and orphanRemoval tie proposal rows to the assignment lifecycle,
     * so removing an assignment also clears its proposals.
     */
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CleanupProposal> proposals;

    /*
     * Optional work diary written by the cleaner while the site is IN_PROGRESS.
     *
     * Empty for a quick one-day cleanup; several entries for a multi-day drive.
     * Tied to the assignment lifecycle exactly like proposals.
     */
    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CleanupActivityLog> activityLogs;
}
