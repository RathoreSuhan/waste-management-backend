package com.cleanbharat.wastemanagement.dto;

import lombok.Data;

@Data // Generates getters, setters, toString(), equals(), hashCode()
public class MunicipalCorporationRequest {

    private String city; // City name
    private String organizationName; // Municipal corporation name
    private String phone; // Contact number
    private String email; // Contact email
}