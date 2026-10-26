package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Response carrying the authenticated user's profile details.
 *
 * profilePic is nullable: when the user has not uploaded a profile picture
 * the value is null.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileResponse {

    private String name;
    private String email;
    private String profilePic;
    private String role;
}