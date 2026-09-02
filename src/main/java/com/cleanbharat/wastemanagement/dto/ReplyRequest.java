package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyRequest {

    // Reply text entered by user
    // 1000 matches both the reply box and the comments.message column width
    @NotBlank(message = "Reply cannot be empty")
    @Size(max = 1000, message = "Reply cannot exceed 1000 characters")
    private String message;
}