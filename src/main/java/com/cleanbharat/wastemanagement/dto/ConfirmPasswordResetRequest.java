package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The final password-recovery submission: the one-time token issued by the
 * verify step plus the new password. The current password is deliberately
 * absent - the verified OTP is the proof of identity here.
 *
 * The new-password limits mirror ChangePasswordRequest so both flows accept
 * the same value shapes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ConfirmPasswordResetRequest {

    @NotBlank(message = "Reset token is required")
    private String resetToken;

    // 72 is all BCrypt reads, so a longer password would be truncated
    @NotBlank(message = "New password is required")
    @Size(min = 6, max = 72, message = "New password must contain 6 to 72 characters")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;
}
