package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The response to a successful recovery-OTP verification: a short-lived,
 * single-use token that authorizes exactly one password change on the
 * account that verified. The token lives in Redis only as its SHA-256 hash
 * and is never logged.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordResetTokenResponse {

    private String resetToken;

    private String message;

    private LocalDateTime timestamp;
}
