package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/*
 * Optional day-by-day work diary kept by the cleaner during an active cleanup.
 *
 * Phase 16 (municipal-authorized workflow, cleaner execution stage):
 * once a cleaner presses START CLEANUP the assignment moves to IN_PROGRESS and
 * they may record as many entries as they like - one per day, per shift, or per
 * milestone. Large multi-day drives therefore get a readable timeline for the
 * municipal officer who signs off later.
 *
 * IMPORTANT: logging is entirely OPTIONAL. A small one-day cleanup can go
 * straight from START CLEANUP to the proof upload without a single entry here,
 * so nothing in the workflow ever requires a row in this table.
 */
@Entity
@Table(name = "cleanup_activity_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Assignment this entry belongs to.
     *
     * An activity log has no meaning on its own - it always documents work done
     * on one specific cleanup assignment, so the link is mandatory.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private CleanupAssignment assignment;

    /*
     * Cleaner who wrote the entry.
     *
     * Stored explicitly (rather than read through the assignment) so the audit
     * trail survives even if the assignment is ever re-awarded.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cleaner_id", nullable = false)
    private User cleaner;

    // What was done: "Cleared the north kerb, filled 12 bags" etc.
    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    /*
     * When the described work actually happened.
     *
     * Kept separate from createdAt so a cleaner can log yesterday's shift the
     * next morning - this is what makes multi-day cleanups read naturally.
     */
    @Column(name = "activity_at", nullable = false)
    private LocalDateTime activityAt;

    // Optional progress photograph stored on Cloudinary
    @Column(name = "image_url")
    private String imageUrl;

    // Optional coordinates captured on the device when the entry was written
    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    /*
     * Distance in metres between the captured position and the reported site.
     *
     * Informational only: an activity entry is a diary note, not proof, so a
     * far-away reading is recorded rather than rejected.
     */
    @Column(name = "distance_meters")
    private Double distanceMeters;

    // Server-side audit stamp, never supplied by the client
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {

        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        // Fall back to "logged right now" when the cleaner did not pick a time
        if (activityAt == null) {
            activityAt = now;
        }
    }
}