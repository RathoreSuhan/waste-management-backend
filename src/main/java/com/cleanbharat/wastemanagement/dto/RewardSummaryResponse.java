package com.cleanbharat.wastemanagement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardSummaryResponse {

    // Cleaner name
    private String cleanerName;

    // Cached total reward points
    private Integer totalRewardPoints;
}