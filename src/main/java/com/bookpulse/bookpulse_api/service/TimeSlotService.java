package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import org.springframework.stereotype.Service;

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
            LocalDateTime dateRequested,
            LocalTime workStart,
            LocalTime workEnd,
            int slotDurationMinutes,
            List<Appointment> existingAppointments) {

        if (workStart.isAfter(workEnd) || slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("La configuración horaria o la duración del slot no son válidas.");
        }

        List<LocalDateTime> availableSlots = new ArrayList<>();

        // Establecemos el punto de inicio combinando el día solicitado con la hora de apertura
        LocalDateTime currentSlot = dateRequested.with(workStart);
        LocalDateTime endOfWork = dateRequested.with(workEnd);

        // Iteramos a lo largo de toda la jornada laboral incrementando por la duración del servicio
        while (currentSlot.plusMinutes(slotDurationMinutes).isBefore(endOfWork) ||
                currentSlot.plusMinutes(slotDurationMinutes).isEqual(endOfWork)) {

            LocalDateTime slotEnd = currentSlot.plusMinutes(slotDurationMinutes);

            // Verificamos si este hueco específico se solapa con alguna cita ya existente
            boolean isOccupied = isSlotOverlapping(currentSlot, slotEnd, existingAppointments);

            // Si el hueco está libre, lo añadimos a las opciones que verá el cliente en Angular
            if (!isOccupied) {
                availableSlots.add(currentSlot);
            }

            // Avanzamos el puntero al siguiente bloque de tiempo
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
            if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
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
