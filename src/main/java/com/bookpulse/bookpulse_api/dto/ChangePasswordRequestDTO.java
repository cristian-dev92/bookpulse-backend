package com.bookpulse.bookpulse_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequestDTO {
    @NotBlank(message = "La contraseña actual es obligatoria")
    private String currentPassword;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    @Size(min = 4, message = "La contraseña debe tener al menos 4 caracteres")
    private String newPassword;
}