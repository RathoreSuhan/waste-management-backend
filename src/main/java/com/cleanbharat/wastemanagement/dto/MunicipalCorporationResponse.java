package com.cleanbharat.wastemanagement.dto;

import lombok.Builder;
import lombok.Data;

@Data // Generates getters, setters, toString()
@Builder
public class MunicipalCorporationResponse {

    private Long id; // Municipal corporation ID
    private String city; // Service-area display name (verbatim for legacy rows)
    private String ulbCode; // LGD ULB code this corporation is accountable for (null until mapped)
    private String organizationName; // Municipal corporation name
    private String phone; // Contact number
    private String email; // Contact email
}