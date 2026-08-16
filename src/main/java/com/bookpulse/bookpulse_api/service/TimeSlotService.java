package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio encargado de la lógica de negocio orientada a la gestión del tiempo.
 * <p>
 * Su función principal es actuar como un motor de cálculo que, basándose en una
 * jornada laboral y las citas ya existentes en la base de datos, genera de forma
 * dinámica los huecos horarios (Time Slots) disponibles para los clientes.
 * </p>
 *
 * @author Cristian
 * @since 1.0
 */
@Service
public class TimeSlotService {

    /**
     * Calcula los huecos horarios disponibles para un día concreto.
     * <p>
     * El algoritmo toma la hora de inicio y fin de la jornada, y va fragmentando
     * el tiempo en intervalos según la duración del servicio. Si un fragmento coincide
     * o se solapa con una cita ya reservada o pendiente, se descarta.
     * </p>
     *
     * @param dateRequested      La fecha del día que se quiere consultar (ej: 2026-06-15).
     * @param workStart          Hora en la que empieza a trabajar el negocio (ej: 09:00).
     * @param workEnd            Hora en la que termina la jornada laboral (ej: 18:00).
     * @param slotDurationMinutes Duración en minutos del servicio (ej: 30, 45, 60).
     * @param existingAppointments Lista de citas ya registradas en la base de datos para ese día.
     * @return Una lista de objetos {@link LocalDateTime} que representan el inicio de cada hueco libre.
     * @throws IllegalArgumentException Si los parámetros de tiempo son incoherentes (ej: fin antes del inicio).
     */
    public List<LocalDateTime> generateAvailableSlots(
            LocalDate dateRequested,
            LocalTime workStart,
            LocalTime workEnd,
            int slotDurationMinutes,
            List<Appointment> existingAppointments) {

        if (workStart.isAfter(workEnd) || workStart.equals(workEnd) || slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("La configuración horaria o la duración del slot no son válidas.");
        }

        List<LocalDateTime> availableSlots = new ArrayList<>();

        // Combinar fecha con hora de inicio y fin correctamente
        LocalDateTime currentSlot = dateRequested.atTime(workStart);
        LocalDateTime endOfWork = dateRequested.atTime(workEnd);
        LocalDateTime now = LocalDateTime.now();

        // Iteramos a lo largo de toda la jornada laboral incrementando por la duración del servicio
        while (!currentSlot.plusMinutes(slotDurationMinutes).isAfter(endOfWork)) {
            LocalDateTime slotEnd = currentSlot.plusMinutes(slotDurationMinutes);

            // Evitamos ofrecer huecos que ya hayan pasado en el día de hoy
            if (currentSlot.isBefore(now)) {
                currentSlot = slotEnd;
                continue;
            }

            boolean isOccupied = isSlotOverlapping(currentSlot, slotEnd, existingAppointments);

            if (!isOccupied) {
                availableSlots.add(currentSlot);
            }

            currentSlot = slotEnd;
        }

        return availableSlots;
    }

    /**
     * Comprueba si un rango horario específico se solapa con alguna de las citas existentes.
     *
     * @param slotStart Inicio del hueco a comprobar.
     * @param slotEnd   Fin del hueco a comprobar.
     * @param appointments Lista de citas reservadas o pendientes.
     * @return {@code true} si el hueco está ocupado por otra cita, {@code false} si está completamente libre.
     */
    private boolean isSlotOverlapping(LocalDateTime slotStart, LocalDateTime slotEnd, List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            // Ignoramos las citas canceladas a la hora de calcular huecos libres
            if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.AVAILABLE) {
                continue;
            }

            // Lógica de solapamiento de intervalos de tiempo:
            // Un hueco se solapa si empieza antes del fin de la cita Y termina después del inicio de la cita.
            if (slotStart.isBefore(appointment.getEndTime()) && slotEnd.isAfter(appointment.getStartTime())) {
                return true;
            }
        }
        return false;
    }
}
