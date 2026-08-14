package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.ServiceDTO;
import com.bookpulse.bookpulse_api.model.Service;
import com.bookpulse.bookpulse_api.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
public class ServiceService {

    private final ServiceRepository serviceRepository;

    @Autowired
    public ServiceService(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Transactional(readOnly = true)
    public List<Service> getActiveServices() {
        return serviceRepository.findByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Service> getAllServicesForAdmin() {
        return serviceRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Service getServiceById(Long id) {
        return serviceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Servicio no encontrado con ID: " + id));
    }

    @Transactional
    public Service createService(ServiceDTO dto) {
        Service service = Service.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .durationMinutes(dto.getDurationMinutes())
                .price(dto.getPrice())
                .active(dto.getActive() != null ? dto.getActive() : true)
                .build();

        return serviceRepository.save(service);
    }

    @Transactional
    public Service updateService(Long id, ServiceDTO dto) {
        Service service = getServiceById(id);

        service.setName(dto.getName());
        service.setDescription(dto.getDescription());
        service.setDurationMinutes(dto.getDurationMinutes());
        service.setPrice(dto.getPrice());
        if (dto.getActive() != null) {
            service.setActive(dto.getActive());
        }

        return serviceRepository.save(service);
    }

    @Transactional
    public void deleteOrDisableService(Long id) {
        Service service = getServiceById(id);
        // Borrado lógico para mantener la integridad con citas pasadas
        service.setActive(false);
        serviceRepository.save(service);
    }
}