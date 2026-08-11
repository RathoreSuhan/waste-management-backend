package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One person's engagement with one report.
 *
 * A row carries two independent things, either of which may be absent:
 *
 *   rating - how urgent this person judged the garbage to be (1-5)
 *   liked  - whether this person appreciated the cleanup of it
 *
 * They live together because both answer "what did this user do about
 * this report", and because the unique constraint below then guarantees
 * one rating and one like per person per report. A like recorded in its
 * own row would need its own key to make that same promise.
 *
 * A row created by a like alone has a null rating, and is excluded from
 * the urgency average and from the "votes submitted" statistics - it is
 * not a vote.
 */
@Entity // Creates votes table
@Table(
        name = "votes",

        // One citizen can vote only once on a report,
        // and likewise appreciate its cleanup only once
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"user_id", "report_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Rating value (1-5)
    // Null when this row exists only to record a like
    private Integer rating;

    /*
     * Whether this person appreciated the cleanup of this report.
     *
     * Nullable on purpose. Null means "no like", so withdrawing one
     * leaves no trace and an unliked report costs nothing to store.
     * A NOT NULL column could also not be added to a table that
     * already holds rows without rewriting them first.
     */
    private Boolean liked;

    // Citizen who voted
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Report being rated
    @ManyToOne
    @JoinColumn(name = "report_id", nullable = false)
    private GarbageReport report;

    // Vote creation timestamp
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}