package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteResponse {

    // Report receiving the vote
    private Long reportId;

    // Citizen rating
    private Integer rating;

    // Citizen name
    private String votedBy;

    // Updated average rating
    private Double urgencyScore;
}