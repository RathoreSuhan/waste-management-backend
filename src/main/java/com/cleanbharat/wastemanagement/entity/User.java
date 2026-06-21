package com.cleanbharat.wastemanagement.entity;

import com.cleanbharat.wastemanagement.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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
    private Role role;                  // User's role (ADMIN, CITIZEN, CLEANER)

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;    // Account creation time

    // Runs automatically before insert
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}