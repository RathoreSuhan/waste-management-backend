package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.Role;
import lombok.*;

/**
 * The answer to a sign-up attempt, which is one of two things.
 *
 * Code sent: verificationRequired is true, there is no token, and email is the
 * address the code went to. Nothing has been created at this point - the form is
 * sent again with the code to finish.
 *
 * Signed in: verificationRequired is false and token carries the ordinary Clean
 * Bharat JWT - the same token POST /api/auth/login issues, read by the same
 * filter under the same role rules.
 *
 * Kept separate from AuthResponse rather than adding a field to it, so the
 * password sign-in contract the municipal console depends on is untouched. The
 * old endpoint returned a bare string, which cannot express two outcomes.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterResponse {

    // True when a code has just been emailed and is still needed
    private boolean verificationRequired;

    // Clean Bharat JWT, null while verification is still required
    private String token;

    // The address the code was sent to, or the new account's address
    private String email;

    // Null while verification is still required - no account exists yet
    private Role role;
}
