package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.ServiceDTO;
import com.bookpulse.bookpulse_api.model.Service;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import com.bookpulse.bookpulse_api.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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

    /**
     * Lectura pública del catálogo de servicios activos.
     * <p>
     * El resultado se almacena en la caché {@code services} para evitar consultas
     * repetidas a la BD cuando el cliente navega o reserva. La caché se invalida
     * automáticamente ({@link CacheEvict}) en cada creación, modificación o borrado
     * de servicio desde el panel de administración.
     * </p>
     *
     * @return Lista de servicios activos.
     */
    @Cacheable(value = "services", key = "'active'")
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

    /**
     * Crea un nuevo servicio y vacía la caché pública de catálogo para que la
     * nueva oferta esté disponible de inmediato para los clientes.
     */
    @CacheEvict(value = "services", allEntries = true)
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

    /**
     * Actualiza un servicio y vacía la caché pública de catálogo.
     */
    @CacheEvict(value = "services", allEntries = true)
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

    /**
     * Borrado lógico del servicio (desactivación) y vaciado de la caché pública.
     */
    @CacheEvict(value = "services", allEntries = true)
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
     * También se vacía la caché pública de catálogo.
     * </p>
     *
     * @param id Identificador del servicio.
     * @throws IllegalStateException si el servicio tiene citas asociadas.
     */
    @CacheEvict(value = "services", allEntries = true)
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