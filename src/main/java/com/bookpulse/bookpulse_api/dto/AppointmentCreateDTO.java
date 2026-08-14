package com.bookpulse.bookpulse_api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Future;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO para la creación y reserva de una nueva cita.
 *
 * @author Cristian
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentCreateDTO {

    /** Fecha y hora de inicio seleccionada por el usuario. */
    @NotNull(message = "La fecha y hora de inicio es obligatoria")
    @Future(message = "La fecha de reserva debe ser en el futuro")
    private LocalDateTime startTime;

    /** ID del servicio que se va a contratar. */
    @NotNull(message = "El ID del servicio es obligatorio")
    private Long serviceId;

    /** ID del usuario que reserva la cita. */
    @NotNull(message = "El ID del usuario es obligatorio")
    private Long userId;

    /** Notas u observaciones opcionales dejadas por el cliente. */
    private String notes;
}