package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                    // Primary Key

    @Column(nullable = false)
    private String name;                // User's name

    @Column(nullable = false, unique = true)
    private String email;               // Email should be unique

    @Column(nullable = false)
    private String password;            // Encrypted password will be stored

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;                  // User's role

    // Cleaner category
    @Enumerated(EnumType.STRING)
    private CleanerType cleanerType;

    // Total reward points
    @Builder.Default
    private Integer rewardPoints = 0;

    // Organization name
    private String organizationName;

    @OneToMany(mappedBy = "cleaner")
    @Builder.Default
    private List<CleanupAssignment> cleanupAssignments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;    // Account creation time

    // Runs automatically before insert
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();

        // Default reward points
        if (rewardPoints == null) {
            rewardPoints = 0;
        }
    }
}