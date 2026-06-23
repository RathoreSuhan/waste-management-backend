package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.ReportStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "garbage_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GarbageReport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // report id

    private String title; // report title

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

    private String imageUrl; // cloudinary image url

    private Double urgencyScore;    // Average citizen rating (1-5)

    @Enumerated(EnumType.STRING)
    private ReportStatus status; // PENDING, IN_PROGRESS, RESOLVED

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt; // report creation timestamp

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // report creator
}