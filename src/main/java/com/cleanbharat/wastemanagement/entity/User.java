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

    // City where the user belongs (the directory's ULB name)
    @Column(nullable = false)
    private String city;

    /*
      District where the user belongs, in the same canonical shape as state and
      city. Nullable only for accounts registered before the directory captured
      the district: cleaner eligibility and the district leaderboard compare
      this value against a report's district, and a row without one is treated
      as not yet reconciled (fail-closed, never matched by city name).
    */
    @Column(length = 100)
    private String district;

    /*
      The LGD directory's ULB code behind this account's city. Nullable for the
      same legacy reason as district; new accounts always carry it, and it is
      what ties the account to one exact urban local body.
    */
    @Column(name = "ulb_code", length = 20)
    private String ulbCode;

    @OneToMany(mappedBy = "cleaner")
    @Builder.Default
    private List<CleanupAssignment> cleanupAssignments = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;    // Account creation time

    // Cloudinary secure URL of the user's profile picture; nullable because
    // adding a profile picture is optional.
    @Column(name = "profile_pic", nullable = true)
    private String profilePic;

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