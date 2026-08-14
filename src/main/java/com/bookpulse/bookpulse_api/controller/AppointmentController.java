package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AppointmentCreateDTO;
import com.bookpulse.bookpulse_api.dto.AppointmentResponseDTO;
import com.bookpulse.bookpulse_api.mapper.AppointmentMapper;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.service.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // 1. GET /api/v1/appointments -> Devuelve SOLO las citas del usuario con sesión activa
    @GetMapping
    public ResponseEntity<List<AppointmentResponseDTO>> getMyAppointments(@AuthenticationPrincipal User currentUser) {
        // En lugar de getAllAppointments(), llamamos al servicio pasando la ID del usuario logueado
        List<Appointment> appointments = appointmentService.getAppointmentsByUserId(currentUser.getId());
        List<AppointmentResponseDTO> dtos = appointments.stream()
                .map(appointmentMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Obtiene la lista de horas de inicio disponibles para reservar en una fecha concreta.
     * <p>
     * Ejemplo de uso: {@code GET /api/v1/appointments/available?date=2026-06-15}
     * </p>
     *
     * @param date Fecha a consultar en formato ISO (yyyy-MM-dd).
     * @return Una respuesta HTTP 200 OK con la lista de {@link LocalDateTime} disponibles.
     */
    // 2. GET /api/v1/appointments/available?date=YYYY-MM-DD -> Huecos libres
    @GetMapping("/available")
    public ResponseEntity<List<LocalDateTime>> getAvailableSlots(
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        List<LocalDateTime> availableSlots = appointmentService.getAvailableSlotsForDay(date);
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
    public ResponseEntity<AppointmentResponseDTO> cancelAppointment(@PathVariable Long id) {
        Appointment cancelledAppointment = appointmentService.cancelAppointment(id);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(cancelledAppointment));
    }

    // 5. PUT /api/v1/appointments/{id}/reschedule?newStartTime=... -> Reprogramar cita
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<AppointmentResponseDTO> rescheduleAppointment(
            @PathVariable Long id,
            @RequestParam("newStartTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime newStartTime) {
        Appointment updatedAppointment = appointmentService.rescheduleAppointment(id, newStartTime);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(updatedAppointment));
    }

    //6. GET /api/v1/appointments/admin/all -> Listar todas las citas para el Administrador
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AppointmentResponseDTO>> getAllAppointmentsForAdmin() {
        List<Appointment> allAppointments = appointmentService.getAllAppointments();
        List<AppointmentResponseDTO> dtos = allAppointments.stream()
                .map(appointmentMapper::toResponseDTO)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    //7. PATCH /api/v1/appointments/{id}/status?status=CONFIRMED -> Cambiar estado de la cita
    @PatchMapping("/{id}/status")
    public ResponseEntity<AppointmentResponseDTO> updateAppointmentStatus(
            @PathVariable Long id,
            @RequestParam("status") AppointmentStatus status) {
        Appointment updated = appointmentService.updateAppointmentStatus(id, status);
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(updated));
    }

}