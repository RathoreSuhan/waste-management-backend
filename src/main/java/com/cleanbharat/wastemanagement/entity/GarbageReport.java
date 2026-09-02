package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.ReportStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
        name = "garbage_reports",
        indexes = {

                // Used during duplicate report detection
                @Index(
                        name = "idx_report_pincode",
                        columnList = "pincode"
                ),

                // Used for latitude/longitude bounding-box filtering
                @Index(
                        name = "idx_report_location",
                        columnList = "latitude,longitude"
                ),

                // Used for recent-report filtering
                @Index(
                        name = "idx_report_created_at",
                        columnList = "createdAt"
                )
        }
)
public class GarbageReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // report id

    @NotBlank
    private String title; // report title

    // Width matches CreateReportRequest's @Size(max = 500), so a valid request always fits
    @Column(length = 500)
    private String description; // garbage details

    @Column(nullable = false)
    private Double latitude; // GPS latitude

    @Column(nullable = false)
    private Double longitude; // GPS longitude

    @Column(nullable = false)
    private String address; // full address

    private String landmark; // nearby landmark

    @Column(nullable = false)
    private String city; // city name

    @Column(nullable = false)
    private String state; // state name

    @Column(nullable = false)
    private String pincode; // postal code

    private String imageUrl; // Cloudinary image url

    private Double urgencyScore;    // Average citizen rating (1-5)

    private Double engagementScore; // urgency + discussion score

    @Enumerated(EnumType.STRING)
    private ReportStatus status; // PENDING, IN_PROGRESS, RESOLVED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // report creation timestamp

    /**
     * Garbage category identified by AI.
     * Example:
     * Plastic Garbage
     * Organic Garbage
     * Medical Garbage
     */
    @Column(length = 100)
    private String garbageCategory;


    @OneToMany(
            mappedBy = "report",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<CleanupAssignment> assignments = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // report creator
}