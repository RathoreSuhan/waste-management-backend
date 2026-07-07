package com.cleanbharat.wastemanagement.dto.admin;

import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Detailed information about a single user for the Admin Portal.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailsResponse {

    // User ID
    private Long id;

    // Full name
    private String name;

    // Email
    private String email;

    // Role
    private Role role;

    // Cleaner category
    private CleanerType cleanerType;

    // Organization name
    private String organizationName;

    // User location
    private String state;
    private String city;

    // Reward points
    @Builder.Default
    private Integer rewardPoints = 0;

    // Activity statistics
    private Long completedCleanups;

    private Long reportsCreated;

    private Long comments;

    private Long votes;

    // Account creation date
    private LocalDateTime createdAt;
}