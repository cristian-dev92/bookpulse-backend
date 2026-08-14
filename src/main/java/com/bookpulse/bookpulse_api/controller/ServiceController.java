package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.ServiceDTO;
import com.bookpulse.bookpulse_api.dto.ServiceResponseDTO;
import com.bookpulse.bookpulse_api.mapper.ServiceMapper;
import com.bookpulse.bookpulse_api.model.Service;
import com.bookpulse.bookpulse_api.service.ServiceService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión del catálogo de servicios.
 *
 * @author Cristian
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/services")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class ServiceController {

    private final ServiceService serviceService;
    private final ServiceMapper serviceMapper;

    @Autowired
    public ServiceController(ServiceService serviceService, ServiceMapper serviceMapper) {
        this.serviceService = serviceService;
        this.serviceMapper = serviceMapper;
    }

    // 1. GET /api/v1/services -> Catálogo público (solo servicios activos para reservar en React)
    @GetMapping
    public ResponseEntity<List<ServiceResponseDTO>> getActiveServices() {
        List<Service> services = serviceService.getActiveServices();
        List<ServiceResponseDTO> dtos = services.stream()
                .map(serviceMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // 2. GET /api/v1/services/{id} -> Detalle de un servicio
    @GetMapping("/{id}")
    public ResponseEntity<ServiceResponseDTO> getServiceById(@PathVariable Long id) {
        Service service = serviceService.getServiceById(id);
        return ResponseEntity.ok(serviceMapper.toResponseDTO(service));
    }

    // 3. GET /api/v1/services/admin/all -> Listar todos los servicios (incluyendo inactivos, solo ADMIN)
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServiceResponseDTO>> getAllServicesForAdmin() {
        List<Service> services = serviceService.getAllServicesForAdmin();
        List<ServiceResponseDTO> dtos = services.stream()
                .map(serviceMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    // 4. POST /api/v1/services -> Crear un nuevo servicio (solo ADMIN)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceResponseDTO> createService(@Valid @RequestBody ServiceDTO dto) {
        Service created = serviceService.createService(dto);
        return new ResponseEntity<>(serviceMapper.toResponseDTO(created), HttpStatus.CREATED);
    }

    // 5. PUT /api/v1/services/{id} -> Actualizar un servicio existente (solo ADMIN)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceResponseDTO> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceDTO dto) {
        Service updated = serviceService.updateService(id, dto);
        return ResponseEntity.ok(serviceMapper.toResponseDTO(updated));
    }

    // 6. DELETE /api/v1/services/{id} -> Desactivar servicio (borrado lógico, solo ADMIN)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> disableService(@PathVariable Long id) {
        serviceService.deleteOrDisableService(id);
        return ResponseEntity.noContent().build();
    }
}