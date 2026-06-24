package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentResponse {

    private Long id;

    private String message;

    private String userName;

    private LocalDateTime createdAt;

    private List<CommentResponse> replies;
}