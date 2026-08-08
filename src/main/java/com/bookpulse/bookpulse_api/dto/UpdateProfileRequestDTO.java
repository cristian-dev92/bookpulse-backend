package com.bookpulse.bookpulse_api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequestDTO {
    @NotBlank(message = "El nombre no puede estar vacío")
    private String name;
    private String email;
    private String phone;
}