package com.bookpulse.bookpulse_api.dto;

import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO para la respuesta de datos de una cita hacia la aplicación frontend.
 *
 * @author Cristian
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppointmentResponseDTO {

    private Long id;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private AppointmentStatus status;
    private BigDecimal price;
    private String notes;

    // Información resumida del usuario
    private Long userId;
    private String userName;
    private String userEmail;

    // Información resumida del servicio
    private Long serviceId;
    private String serviceName;
    private Integer serviceDurationMinutes;
}