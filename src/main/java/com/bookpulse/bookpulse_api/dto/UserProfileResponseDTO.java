package com.bookpulse.bookpulse_api.dto;

import com.bookpulse.bookpulse_api.model.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponseDTO {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private Role role;
}