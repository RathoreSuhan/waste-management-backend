package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    /*
     * Cleaner who earned this reward.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaner_id", nullable = false)
    private User cleaner;


    /*
     * Assignment responsible
     * for earning this reward.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private CleanupAssignment assignment;


    /*
     * Points earned.
     *
     * Example:
     * +50
     */
    @Column(nullable = false)
    private Integer points;


    /*
     * Reason for reward.
     *
     * Example:
     * Cleanup of Report #18
     */
    @Column(nullable = false, length = 300)
    private String reason;


    /*
     * Reward creation time.
     */
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }
}