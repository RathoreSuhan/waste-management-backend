package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteRequest {

    // Report on which citizen is voting
    private Long reportId;

    // Rating between 1 and 5
    private Integer rating;
}