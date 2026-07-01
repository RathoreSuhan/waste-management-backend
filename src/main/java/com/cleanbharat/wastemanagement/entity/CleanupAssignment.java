package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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
}