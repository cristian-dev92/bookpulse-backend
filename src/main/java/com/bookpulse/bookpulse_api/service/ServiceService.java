package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.ServiceDTO;
import com.bookpulse.bookpulse_api.model.Service;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import com.bookpulse.bookpulse_api.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@org.springframework.stereotype.Service
public class ServiceService {

    private final ServiceRepository serviceRepository;
    private final AppointmentRepository appointmentRepository;

    @Autowired
    public ServiceService(ServiceRepository serviceRepository, AppointmentRepository appointmentRepository) {
        this.serviceRepository = serviceRepository;
        this.appointmentRepository = appointmentRepository;
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

    /**
     * Elimina físicamente (borrado definitivo) un servicio del catálogo.
     * <p>
     * Si el servicio ya tiene citas asociadas en la BD, se lanza
     * {@link IllegalStateException} para evitar violaciones de integridad referencial;
     * en ese caso el administrador debe usar el borrado lógico (desactivar).
     * </p>
     *
     * @param id Identificador del servicio.
     * @throws IllegalStateException si el servicio tiene citas asociadas.
     */
    @Transactional
    public void deleteServicePermanently(Long id) {
        Service service = getServiceById(id);

        long appointmentCount = appointmentRepository.countByServiceId(id);
        if (appointmentCount > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar el servicio \"" + service.getName() + "\" porque tiene "
                            + appointmentCount + " cita(s) asociada(s). Desactívalo en su lugar.");
        }

        serviceRepository.delete(service);
    }
}