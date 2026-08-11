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

    /*
      Role of the comment's author, as the enum name - ROLE_CITIZEN,
      ROLE_CLEANER or ROLE_ADMIN.

      Sent so a reader can tell an official reply from a neighbour's
      remark without leaving the thread. The frontend turns the code into
      a designation for display; the raw enum is never shown.
    */
    private String userRole;


    private LocalDateTime createdAt;

    private List<CommentResponse> replies;
}