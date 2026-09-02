package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Sign-up details for a citizen or cleaner account.
 *
 * The maximum lengths mirror registerSchema.js, so the form and the API agree on
 * what will be accepted, and every one of them fits the varchar(255) columns on
 * User - an over-long value is answered with a field message instead of failing
 * as a database error.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name cannot exceed 100 characters")
    private String name;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email;

    /*
      BCrypt only reads the first 72 bytes of a password, so anything longer is
      silently ignored at sign-in. Capping it here keeps the stored hash honest
      and matches the minimum the sign-up form has always enforced.
      LoginRequest deliberately has no maximum, so an account created before this
      rule can still sign in.
    */
    @NotBlank(message = "Password is required")
    @Size(min = 6, max = 72, message = "Password must contain 6 to 72 characters")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    // Required only for cleaners
    private CleanerType cleanerType;

    // Optional (NGO / PRIVATE / MUNICIPAL)
    @Size(max = 150, message = "Organization name cannot exceed 150 characters")
    private String organizationName;

    // User's state
    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State cannot exceed 100 characters")
    private String state;

    // User's city
    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city;
}
