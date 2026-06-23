package com.cleanbharat.wastemanagement.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity // Creates votes table
@Table(
        name = "votes",

        // One citizen can vote only once on a report
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
    private Integer rating;

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