package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RewardHistoryResponse {

    // Reward points earned
    private Integer points;

    // Reason for reward
    private String reason;

    // Reward earned date
    private LocalDateTime createdAt;

}