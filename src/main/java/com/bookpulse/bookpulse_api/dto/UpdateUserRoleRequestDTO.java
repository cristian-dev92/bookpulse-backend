package com.bookpulse.bookpulse_api.dto;

import lombok.Data;

@Data
public class UpdateUserRoleRequestDTO {
    private String role; // "ADMIN" o "USER"
}
