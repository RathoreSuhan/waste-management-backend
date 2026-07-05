package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.CleanerType;
import com.cleanbharat.wastemanagement.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

    @NotBlank(message = "Password is required")
    private String password;

    @NotNull(message = "Role is required")
    private Role role;

    // Required only for cleaners
    private CleanerType cleanerType;

    // Optional (NGO / PRIVATE / MUNICIPAL)
    private String organizationName;

    // User's state
    @NotBlank(message = "State is required")
    private String state;

    // User's city
    @NotBlank(message = "City is required")
    private String city;
}