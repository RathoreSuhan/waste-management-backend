package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Details of a municipal corporation, submitted from the admin console.
 *
 * The limits below mirror municipalCorporationSchema.js on the frontend, so a
 * value the form accepts is a value this API accepts. They also stay well inside
 * the varchar(255) columns Hibernate creates for MunicipalCorporation, which is
 * what stops an over-long entry from failing as a database error instead of a
 * readable field message.
 */
@Data // Generates getters, setters, toString(), equals(), hashCode()
public class MunicipalCorporationRequest {

    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City cannot exceed 100 characters")
    private String city; // City name

    @NotBlank(message = "Organisation name is required")
    @Size(max = 150, message = "Organisation name cannot exceed 150 characters")
    private String organizationName; // Municipal corporation name

    @NotBlank(message = "Contact number is required")
    @Size(max = 20, message = "Contact number cannot exceed 20 characters")
    private String phone; // Contact number

    @NotBlank(message = "Email address is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email cannot exceed 150 characters")
    private String email; // Contact email
}
