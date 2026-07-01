package com.cleanbharat.wastemanagement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIValidationResponse {

    // Are both images from the same location?
    private Boolean sameLocation;

    // Has garbage actually been removed?
    private Boolean garbageRemoved;

    // AI confidence (0.0 - 1.0)
    private Double confidence;

    // AI explanation
    private String remarks;
}