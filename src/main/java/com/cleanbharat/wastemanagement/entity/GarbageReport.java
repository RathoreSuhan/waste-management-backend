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

    @Id // primary key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // auto increment
    private Long id;

    private String title; // report title

    private String description; // garbage details

    private String location; // area name

    private String imageUrl; // image path/url

    @Enumerated(EnumType.STRING) // store enum as text
    private ReportStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;     // report creation time

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @ManyToOne // many reports -> one user
    @JoinColumn(name = "user_id") // foreign key
    private User user;
}