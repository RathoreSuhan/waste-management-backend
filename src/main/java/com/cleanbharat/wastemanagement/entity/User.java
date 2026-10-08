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

    /*
      Google's stable account identifier (the 'sub' claim), for an account that
      signs in with Google.

      'sub' rather than the email: Google treats it as the permanent id for an
      account, while the email on it can be changed. Keying the link on email
      would mean a renamed Google account arriving as a stranger, and a reused
      address arriving as somebody else.

      Unique, so one Google account can never be linked to two Clean Bharat
      accounts. Nullable, because every account created before Google sign-in
      existed still signs in with its password - see AuthService.login.
    */
    @Column(name = "google_subject", unique = true, length = 64)
    private String googleSubject;

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

    // State where the user belongs
    @Column(nullable = false)
    private String state;

    // City where the user belongs
    @Column(nullable = false)
    private String city;

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