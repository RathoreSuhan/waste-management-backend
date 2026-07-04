package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "public_feed_analytics")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicFeedAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Completed cleanup assignment
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "cleanup_assignment_id",
            nullable = false,
            unique = true
    )
    private CleanupAssignment cleanupAssignment;

    // Number of citizens who viewed this cleanup
    @Builder.Default
    private Long viewCount = 0L;

    // Number of likes received
    @Builder.Default
    private Long likeCount = 0L;

    // Number of times shared
    @Builder.Default
    private Long shareCount = 0L;

    // Analytics record creation time
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Last analytics update time
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {

        if (viewCount == null) {
            viewCount = 0L;
        }

        if (likeCount == null) {
            likeCount = 0L;
        }

        if (shareCount == null) {
            shareCount = 0L;
        }

        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}