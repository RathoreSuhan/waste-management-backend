package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Request to update the authenticated user's profile.
 *
 * All fields are optional: a user may change only their name, only their
 * profile picture, or both. The name is not mandatory - a user can update
 * just the profile picture without touching the name.
 *
 * The profile picture is delivered as a secure_url string already uploaded
 * to Cloudinary by the frontend service layer.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    // Name is optional: a user may leave it blank to keep the existing value
    private String name;

    // Cloudinary secure URL of the profile picture; null means no change
    private String profilePic;
}