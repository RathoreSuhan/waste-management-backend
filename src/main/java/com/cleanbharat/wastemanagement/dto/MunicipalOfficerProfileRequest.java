package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * What a municipal officer may change about their own corporation from
 * the profile screen: the three contact details, nothing else.
 *
 * The location identity is deliberately absent. The stored legacy city and
 * the assigned ULB code are routing and mapping values owned by the
 * administrator's mapping workflow - a corporation that could edit them
 * from its own profile could re-route every future report filed in the
 * city. Any city / stateCode / districtCode / ulbCode a client posts
 * alongside these fields is simply not bound by this DTO, so it cannot
 * reach storage through this endpoint.
 *
 * Field limits mirror MunicipalCorporationRequest so both admin and officer
 * paths accept the same value shapes.
 */
@Data // Generates getters, setters, toString(), equals(), hashCode()
public class MunicipalOfficerProfileRequest {

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
