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

    /*
      The official jurisdiction the mapped ulbCode resolves to in the LGD
      directory: state, district, ULB name and body type. Null when the row
      has no mapping, the directory cannot describe the code, or the code is
      ambiguous even against the stored city - never a guessed location.
    */
    private MappedLocation mappedLocation;

    @Data
    @Builder
    public static class MappedLocation {

        private String state; // Official state name
        private String district; // Official district name
        private String ulbName; // Official ULB / municipal-area name
        private String ulbType; // ULB type (Municipal Corporation, Council, ...)
    }
}