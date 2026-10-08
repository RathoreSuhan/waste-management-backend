package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.Role;
import lombok.*;

/**
 * The answer to a Google sign-in attempt, which is one of two things.
 *
 * Signed in: registrationRequired is false and token carries the ordinary Clean
 * Bharat JWT - the same token POST /api/auth/login issues, used by the same
 * filter and the same role rules. Google's own token is never handed back.
 *
 * Not signed in yet: registrationRequired is true, there is no token, and email
 * and name are the verified details the completion form should show. Nothing has
 * been created at this point and nothing is authorised; the browser must come
 * back with the same credential plus the missing profile details.
 *
 * Kept separate from AuthResponse rather than adding two fields to it, so the
 * password sign-in contract that the municipal console depends on is untouched.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleAuthResponse {

    // True when this Google account has no Clean Bharat account yet
    private boolean registrationRequired;

    // Clean Bharat JWT, null while registration is still required
    private String token;

    // Verified Google address: the new account's email, or the signed-in one
    private String email;

    // Display name from Google, to prefill the completion form
    private String name;

    // Null while registration is still required - the role is not chosen yet
    private Role role;
}
