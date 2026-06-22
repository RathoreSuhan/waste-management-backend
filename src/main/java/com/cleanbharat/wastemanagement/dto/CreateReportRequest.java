package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateReportRequest {

    @NotBlank(message = "Title is required")
    private String title; // report title

    @NotBlank(message = "Description is required")
    private String description; // garbage details

    @NotNull(message = "Latitude is required")
    private Double latitude; // GPS latitude

    @NotNull(message = "Longitude is required")
    private Double longitude; // GPS longitude

    @NotBlank(message = "Address is required")
    private String address; // complete address

    private String landmark; // nearby landmark (optional)

    @NotBlank(message = "City is required")
    private String city; // city name

    @NotBlank(message = "State is required")
    private String state; // state name

    @NotBlank(message = "Pincode is required")
    private String pincode; // postal code
}