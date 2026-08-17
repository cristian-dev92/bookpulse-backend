package com.bookpulse.bookpulse_api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * DTO para la reprogramación de una cita existente.
 * <p>
 * Permite cambiar la fecha y hora de inicio y, opcionalmente, el servicio
 * asociado a la cita. Si {@code serviceId} no se envía, se mantiene el servicio actual.
 * </p>
 *
 * @author Cristian
 * @since 1.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentRescheduleDTO {

    /** Nueva fecha y hora de inicio de la cita. */
    @NotNull(message = "La nueva fecha y hora de inicio es obligatoria")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime newDateTime;

    /** Nuevo servicio a aplicar (opcional). Si es null se conserva el servicio actual. */
    private Long serviceId;
}