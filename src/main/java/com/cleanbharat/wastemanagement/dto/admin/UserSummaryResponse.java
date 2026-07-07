package com.cleanbharat.wastemanagement.dto.admin;

import com.cleanbharat.wastemanagement.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lightweight user information displayed in the Admin User List.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponse {

    // User ID
    private Long id;

    // Full name
    private String name;

    // Email address
    private String email;

    // User role
    private Role role;

    // User's state
    private String state;

    // User's city
    private String city;

    // Total reward points
    @Builder.Default
    private Integer rewardPoints = 0;

    // Account creation date
    private LocalDateTime createdAt;
}