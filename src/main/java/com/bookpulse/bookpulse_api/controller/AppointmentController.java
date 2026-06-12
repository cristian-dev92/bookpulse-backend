package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.service.AppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "http://localhost:4200") // Permite la conexión nativa con Angular
public class AppointmentController {

    private final AppointmentService appointmentService;

    /**
     * Inyección de dependencias del servicio de citas.
     *
     * @param appointmentService Servicio de lógica de negocio de citas.
     */
    @Autowired
    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    /**
     * Obtiene la lista de horas de inicio disponibles para reservar en una fecha concreta.
     * <p>
     * Ejemplo de uso: {@code GET /api/appointments/available?date=2026-06-15}
     * </p>
     *
     * @param date Fecha a consultar en formato ISO (yyyy-MM-dd).
     * @return Una respuesta HTTP 200 OK con la lista de {@link LocalDateTime} disponibles.
     */
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
     * @param startTime Fecha y hora exacta del hueco que el cliente desea bloquear.
     * @return Una respuesta HTTP 201 CREATED con el objeto {@link Appointment} generado.
     */
    @PostMapping("/reserve")
    public ResponseEntity<Appointment> reserveSlot(
            @RequestParam("startTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime) {

        Appointment currentReservation = appointmentService.reserveSlot(startTime);
        return new ResponseEntity<>(currentReservation, HttpStatus.CREATED);
    }
}