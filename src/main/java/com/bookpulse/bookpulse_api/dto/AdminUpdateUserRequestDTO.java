package com.bookpulse.bookpulse_api.dto;

import lombok.Data;

@Data
public class AdminUpdateUserRequestDTO {
    private String name;
    private String email;
    private String phone;
    private String role;
    private String newPassword; // Opcional: si viene con texto, se actualiza
    private Boolean active;
}
