package com.cleanbharat.wastemanagement.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplyRequest {

    // Reply text entered by user
    private String message;
}