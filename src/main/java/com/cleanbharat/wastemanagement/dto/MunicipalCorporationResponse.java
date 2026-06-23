package com.cleanbharat.wastemanagement.dto;

import lombok.Builder;
import lombok.Data;

@Data // Generates getters, setters, toString()
@Builder
public class MunicipalCorporationResponse {

    private Long id; // Municipal corporation ID
    private String city; // City name
    private String organizationName; // Municipal corporation name
    private String phone; // Contact number
    private String email; // Contact email
}