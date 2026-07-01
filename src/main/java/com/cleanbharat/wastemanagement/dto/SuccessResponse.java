package com.cleanbharat.wastemanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuccessResponse {

    // Success message
    private String message;

    // Response timestamp
    private LocalDateTime timestamp;

}