package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    // No maximum here: an account created before the 72 character rule must
    // still be able to prove its current password
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    // 72 is all BCrypt reads, so a longer new password would be truncated
    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 72, message = "New password must contain 6 to 72 characters")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}