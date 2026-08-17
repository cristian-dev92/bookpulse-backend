package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AppointmentCreateDTO;
import com.bookpulse.bookpulse_api.dto.AppointmentRescheduleDTO;
import com.bookpulse.bookpulse_api.dto.AppointmentResponseDTO;
import com.bookpulse.bookpulse_api.mapper.AppointmentMapper;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Controlador REST que expone los endpoints públicos para la gestión de citas y reservas.
 * <p>
 * Proporciona los puntos de entrada necesarios para consultar disponibilidad horaria
 * y realizar pre-reservas en tiempo real desde el cliente frontend.
 * </p>
 *
 * @author Cristian
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/appointments")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"}) // Configuración para React (Vite / CRA)
public class AppointmentController {

    private final AppointmentService appointmentService;
    private final AppointmentMapper appointmentMapper;

    /**
     * Inyección de dependencias del servicio de citas.
     *
     * @param appointmentService Servicio de lógica de negocio de citas.
     */
    @Autowired
    public AppointmentController(AppointmentService appointmentService, AppointmentMapper appointmentMapper) {
        this.appointmentService = appointmentService;
        this.appointmentMapper = appointmentMapper;
    }

    // 1. GET /api/v1/appointments/my-appointments?page=0&size=5&statuses=PENDING,CONFIRMED -> Citas del usuario (paginadas)
    @GetMapping("/my-appointments")
    public ResponseEntity<Page<AppointmentResponseDTO>> getMyAppointments(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestParam(value = "statuses", required = false) List<AppointmentStatus> statuses) {
        requireAuthenticated(currentUser);
        Page<Appointment> appointments = appointmentService.getMyAppointments(
                currentUser.getId(),
                statuses,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")));
        Page<AppointmentResponseDTO> dtos = appointments.map(appointmentMapper::toResponseDTO);
        return ResponseEntity.ok(dtos);
    }

    /**
     * Obtiene la lista de horas de inicio disponibles para reservar en una fecha concreta.
     * <p>
     * Ejemplo de uso: {@code GET /api/v1/appointments/available?date=2026-06-15}
     * </p>
     *
     * @param date                Fecha a consultar en formato ISO (yyyy-MM-dd).
     * @param excludeAppointmentId Si se está reprogramando una cita, su ID para que su hora
     *                             actual no se considere ocupada (opcional).
     * @return Una respuesta HTTP 200 OK con la lista de {@link LocalDateTime} disponibles.
     */
    // 2. GET /api/v1/appointments/available?date=YYYY-MM-DD -> Huecos libres
    @GetMapping({"/available", "/available-slots"})
    public ResponseEntity<List<LocalDateTime>> getAvailableSlots(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(value = "excludeAppointmentId", required = false) Long excludeAppointmentId) {
        List<LocalDateTime> availableSlots = appointmentService.getAvailableSlotsForDay(date, excludeAppointmentId);
        return ResponseEntity.ok(availableSlots);
    }

    /**
     * Inicia la pre-reserva de un hueco horario poniéndolo en estado pendiente.
     * <p>
     * Ejemplo de uso: {@code POST /api/appointments/reserve?startTime=2026-06-15T10:00:00}
     * </p>
     *
     * @param currentUser Usuario autenticado que desea reservar el hueco.
     * @return Una respuesta HTTP 201 CREATED con el objeto {@link Appointment} generado.
     */
    // 3. POST /api/v1/appointments/reserve -> Crear/Reservar cita con DTO
    @PostMapping("/reserve")
    public ResponseEntity<AppointmentResponseDTO> reserveSlot(
            @Valid @RequestBody AppointmentCreateDTO dto,
            @AuthenticationPrincipal User currentUser) {
        requireAuthenticated(currentUser);
        // Pasa la fecha de inicio, el usuario autenticado y el ID del servicio
        Appointment currentReservation = appointmentService.reserveSlot(
                dto.getStartTime(),
                currentUser,
                dto.getServiceId()
        );

        return new ResponseEntity<>(appointmentMapper.toResponseDTO(currentReservation), HttpStatus.CREATED);
    }

    // 4. DELETE /api/v1/appointments/{id} -> Cancelar cita
    @DeleteMapping("/{id}")
    public ResponseEntity<AppointmentResponseDTO> cancelAppointment(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {
        requireAuthenticated(currentUser);
        Appointment cancelledAppointment = appointmentService.cancelAppointment(id, currentUser);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(cancelledAppointment));
    }

    // 5. PUT /api/v1/appointments/{id}/reschedule -> Reprogramar cita (JSON: newDateTime + serviceId opcional)
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponseDTO> rescheduleAppointment(
            @PathVariable Long id,
            @Valid @RequestBody AppointmentRescheduleDTO dto,
            @AuthenticationPrincipal User currentUser) {
        requireAuthenticated(currentUser);
        Appointment updatedAppointment = appointmentService.rescheduleAppointment(id, dto, currentUser);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(updatedAppointment));
    }

    //6. GET /api/v1/appointments/admin/all?page=0&size=8&status=CONFIRMED -> Listar citas (Admin, paginado)
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AppointmentResponseDTO>> getAllAppointmentsForAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "8") int size,
            @RequestParam(value = "status", required = false) AppointmentStatus status) {
        Page<Appointment> allAppointments = appointmentService.getAllAppointments(
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startTime")));
        Page<AppointmentResponseDTO> dtos = allAppointments.map(appointmentMapper::toResponseDTO);
        return ResponseEntity.ok(dtos);
    }

    //6bis. GET /api/v1/appointments/admin/calendar?from=...&to=... -> Citas de un rango para el calendario (Admin)
    @GetMapping("/admin/calendar")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponseDTO>> getCalendarAppointments(
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<Appointment> appointments = appointmentService.getAppointmentsBetween(from, to);
        List<AppointmentResponseDTO> dtos = appointments.stream()
                .map(appointmentMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    //7. PATCH /api/v1/appointments/{id}/status?status=CONFIRMED -> Cambiar estado de la cita
    @PatchMapping("/{id}/status")
    public ResponseEntity<AppointmentResponseDTO> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam("status") AppointmentStatus status,
            @AuthenticationPrincipal User currentUser) {
        requireAuthenticated(currentUser);
        Appointment updated = appointmentService.updateAppointmentStatus(id, status, currentUser);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(updated));
    }

    /**
     * Comprueba que la petición provenga de un usuario autenticado.
     * Como /api/v1/appointments/** requiere autenticación, esto es una doble
     * verificación defensiva por si la configuración de seguridad cambiara.
     *
     * @param currentUser Principal de seguridad resuelto por Spring Security.
     */
    private void requireAuthenticated(User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Debes iniciar sesión para realizar esta operación.");
        }
    }

}