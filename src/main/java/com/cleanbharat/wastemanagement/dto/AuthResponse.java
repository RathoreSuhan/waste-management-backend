package com.cleanbharat.wastemanagement.dto;

import com.cleanbharat.wastemanagement.enums.Role;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private String token;

    private String email;

    private Role role;
}