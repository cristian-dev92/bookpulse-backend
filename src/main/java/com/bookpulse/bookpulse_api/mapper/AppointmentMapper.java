package com.bookpulse.bookpulse_api.mapper;

import com.bookpulse.bookpulse_api.dto.AppointmentResponseDTO;
import com.bookpulse.bookpulse_api.model.Appointment;
import org.springframework.stereotype.Component;

/**
 * Componente para transformar entidades {@link Appointment} a sus DTOs correspondientes.
 *
 * @author Cristian
 * @since 1.0
 */
@Component
public class AppointmentMapper {

    public AppointmentResponseDTO toResponseDTO(Appointment appointment) {
        if (appointment == null) {
            return null;
        }

        AppointmentResponseDTO.AppointmentResponseDTOBuilder builder = AppointmentResponseDTO.builder()
                .id(appointment.getId())
                .startTime(appointment.getStartTime())
                .endTime(appointment.getEndTime())
                .status(appointment.getStatus())
                .price(appointment.getPrice())
                .notes(appointment.getNotes());

        if (appointment.getUser() != null) {
            builder.userId(appointment.getUser().getId())
                    .userName(appointment.getUser().getName())
                    .userEmail(appointment.getUser().getEmail());
        }

        if (appointment.getService() != null) {
            builder.serviceId(appointment.getService().getId())
                    .serviceName(appointment.getService().getName())
                    .serviceDurationMinutes(appointment.getService().getDurationMinutes());
        }

        return builder.build();
    }
}