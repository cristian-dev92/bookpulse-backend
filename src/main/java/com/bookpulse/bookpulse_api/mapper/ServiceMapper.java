package com.bookpulse.bookpulse_api.mapper;

import com.bookpulse.bookpulse_api.dto.ServiceResponseDTO;
import com.bookpulse.bookpulse_api.model.Service;
import org.springframework.stereotype.Component;

/**
 * Componente para transformar entidades {@link Service} a DTOs.
 *
 * @author Cristian
 * @since 1.0
 */
@Component
public class ServiceMapper {

    public ServiceResponseDTO toResponseDTO(Service service) {
        if (service == null) {
            return null;
        }

        return ServiceResponseDTO.builder()
                .id(service.getId())
                .name(service.getName())
                .description(service.getDescription())
                .durationMinutes(service.getDurationMinutes())
                .price(service.getPrice())
                .active(service.getActive())
                .build();
    }
}