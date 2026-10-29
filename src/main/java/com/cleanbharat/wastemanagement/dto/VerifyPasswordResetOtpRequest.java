package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The six-digit code submitted during the profile-page password recovery
 * flow. There is deliberately no email or account field: the code is always
 * checked against the authenticated account's own recovery code.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VerifyPasswordResetOtpRequest {

    @NotBlank(message = "The verification code is required")
    @Pattern(regexp = "\\d{6}", message = "Enter the six-digit code from your email")
    private String code;
}
