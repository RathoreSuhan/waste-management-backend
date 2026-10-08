package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The Clean Bharat half of a first-time Google sign-up.
 *
 * Google supplies the identity; it has no idea whether somebody is reporting
 * waste or clearing it, or which city's leaderboard they belong on. Those are
 * the application's own requirements and they survive unchanged from the form
 * this replaces - which is why the limits below are the ones RegisterRequest
 * carried, and registerSchema.js still mirrors them field for field.
 *
 * There is deliberately no email and no password. The address is taken from the
 * verified Google token, so it cannot be edited into somebody else's; and an
 * account reached through Google has no password to set.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GoogleRegisterRequest {

    /*
      Sent again, and verified again on arrival.

      The first call only reported that no account existed yet; it issued
      nothing. Trusting it here - "the previous request said this person is
      verified" - would mean anyone could create an account for any address by
      posting this request on its own. The credential is the only proof, so it
      is re-checked before a single row is written.
    */
    @NotBlank(message = "Google sign-in credential is required")
    @Size(max = 4096, message = "Google sign-in credential is not valid")
    private String credential;

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    // Only ROLE_CITIZEN and ROLE_CLEANER are accepted; see AuthService
    @NotNull(message = "Role is required")
    private Role role;

    // Required only for cleaners
    private CleanerType cleanerType;

    // Optional (NGO / PRIVATE / MUNICIPAL)
    @Size(max = 150, message = "Organization name cannot exceed 150 characters")
    private String organizationName;

    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;
}
