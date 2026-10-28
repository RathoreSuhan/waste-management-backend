package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Incoming payload of POST /api/reports.
 *
 * Bound from multipart/form-data, so the field names here are the form field
 * names the client must send.
 *
 * These constraints are the authoritative ones. The frontend zod schema
 * mirrors them for instant feedback, but it can be bypassed by any direct
 * API call, so the same rules are enforced again here.
 */
@Getter
@Setter
public class CreateReportRequest {

    @NotBlank(message = "Title is required")
    @Size(
            min = 5,
            max = 100,
            message = "Title must be between 5 and 100 characters"
    )
    private String title; // report title

    @NotBlank(message = "Description is required")
    @Size(
            min = 10,
            max = 500,
            message = "Description must be between 10 and 500 characters"
    )
    private String description; // garbage details

    @NotNull(message = "Latitude is required")
    @DecimalMin(
            value = "-90.0",
            message = "Latitude must be between -90 and 90"
    )
    @DecimalMax(
            value = "90.0",
            message = "Latitude must be between -90 and 90"
    )
    private Double latitude; // GPS latitude

    @NotNull(message = "Longitude is required")
    @DecimalMin(
            value = "-180.0",
            message = "Longitude must be between -180 and 180"
    )
    @DecimalMax(
            value = "180.0",
            message = "Longitude must be between -180 and 180"
    )
    private Double longitude; // GPS longitude

    @NotBlank(message = "Address is required")
    @Size(
            min = 5,
            max = 255,
            message = "Address must be between 5 and 255 characters"
    )
    private String address; // complete address

    @Size(
            max = 100,
            message = "Landmark cannot exceed 100 characters"
    )
    private String landmark; // nearby landmark (optional)

    /*
      Where the report is, as free text from the legacy form. Optional only
      because the directory-cascade form sends the three LGD codes below
      instead - the service enforces that exactly one of the two shapes is
      present, and code-backed submissions overwrite these with the canonical
      directory names.
    */
    @Size(
            max = 100,
            message = "City must be at most 100 characters"
    )
    private String city; // city name (legacy free-text path)

    @Size(
            max = 100,
            message = "State must be at most 100 characters"
    )
    private String state; // state name (legacy free-text path)

    /*
      Where the report is, as three LGD codes - the same contract registration
      uses. All three must be sent together and are validated against the
      bundled directory; the stored city/state are then the directory's
      canonical names, and the ULB code is what routes the report to the one
      Municipal Corporation configured for it.
    */
    @Size(max = 20, message = "State code is not valid")
    private String stateCode;

    @Size(max = 20, message = "District code is not valid")
    private String districtCode;

    @Size(max = 20, message = "City or municipal-area code is not valid")
    private String ulbCode;

    @NotBlank(message = "Pincode is required")
    @Pattern(
            regexp = "^[1-9][0-9]{5}$",
            message = "Pincode must be a valid 6 digit number"
    )
    private String pincode; // postal code
}
