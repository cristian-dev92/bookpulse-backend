package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Servicio principal encargado de coordinar la lógica de negocio de las citas.
 * <p>
 * Se comunica con el repositorio para persistir los datos y delega en el
 * {@link TimeSlotService} las operaciones de cálculo de disponibilidad horaria.
 * </p>
 *
 * @author Cristian
 * @since 1.0
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotService timeSlotService;

    /**
     * Inyección de dependencias a través del constructor.
     *
     * @param appointmentRepository Repositorio de citas.
     * @param timeSlotService       Servicio de cálculo de franjas horarias.
     */
    @Autowired
    public AppointmentService(AppointmentRepository appointmentRepository, TimeSlotService timeSlotService) {
        this.appointmentRepository = appointmentRepository;
        this.timeSlotService = timeSlotService;
    }

    /**
     * Obtiene los huecos horarios que están libres para un día determinado.
     * <p>
     * Este método define una jornada laboral estándar ficticia (por ahora) de 09:00 a 18:00
     * con servicios de 60 minutos. Busca las citas existentes en la BD para ese día
     * y le pide al motor de tiempos que calcule los huecos disponibles.
     * </p>
     *
     * @param date El día que se desea consultar.
     * @return Una lista de {@link LocalDateTime} con los inicios de cada turno libre.
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailableSlotsForDay(LocalDate date) {
        // Definimos el rango del día completo (desde las 00:00:00 hasta las 23:59:59)
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // Pasos intermedios profesionales:
        // 1. Recuperamos las citas que ya existan ese día en la BD
        List<Appointment> existingAppointments = appointmentRepository.findByStartTimeBetween(startOfDay, endOfDay);

        // 2. Parámetros de negocio (Hardcodeados provisionalmente para las pruebas)
        LocalTime workStart = LocalTime.of(9, 0);
        LocalTime workEnd = LocalTime.of(18, 0);
        int durationMinutes = 60;

        // 3. Delegamos el cálculo algorítmico al motor de tiempos
        return timeSlotService.generateAvailableSlots(startOfDay, workStart, workEnd, durationMinutes, existingAppointments);
    }

    /**
     * Inicia el proceso de reserva de un hueco horario, poniéndolo en estado temporal.
     * <p>
     * Al estar anotado con {@link Transactional}, si salta un problema de concurrencia
     * (bloqueo optimista) o el hueco ya se ocupó, la base de datos hará un rollback automático.
     * </p>
     *
     * @param startTime Fecha y hora de inicio deseada por el cliente.
     * @return La entidad {@link Appointment} guardada en estado pendiente.
     * @throws IllegalArgumentException Si la hora seleccionada ya se encuentra reservada.
     */
    @Transactional
    public Appointment reserveSlot(LocalDateTime startTime) {
        int durationMinutes = 60; // Debe coincidir con la configuración del cálculo
        LocalDateTime endTime = startTime.plusMinutes(durationMinutes);

        // Validamos en el momento si ya hay alguna cita que se solape en la BD
        // Esto es una doble seguridad antes de intentar insertar el registro
        List<Appointment> overlapping = appointmentRepository.findByStartTimeBetween(startTime, endTime);

        for (Appointment app : overlapping) {
            if (app.getStatus() != AppointmentStatus.CANCELLED) {
                throw new IllegalArgumentException("El hueco seleccionado ya no está disponible.");
            }
        }

        // Creamos la nueva cita en estado PENDING (bloqueo de cortesía de 5 minutos)
        Appointment newAppointment = new Appointment();
        newAppointment.setStartTime(startTime);
        newAppointment.setEndTime(endTime);
        newAppointment.setStatus(AppointmentStatus.PENDING);

        // Al guardar, Hibernate gestiona el campo @Version automáticamente
        return appointmentRepository.save(newAppointment);
    }

    /**
     * Recupera la lista completa de todas las citas registradas en el sistema.
     * <p>
     * Se marca como {@code readOnly = true} para optimizar el rendimiento de la transacción en PostgreSQL.
     * </p>
     *
     * @return Una lista con todas las entidades {@link Appointment}.
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    /**
     * Cancela una cita cambiando su estado a CANCELLED.
     *
     * @param id Identificador de la cita a cancelar.
     * @return La entidad {@link Appointment} actualizada.
     */
    @Transactional
    public Appointment cancelAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + id));

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Reprograma una cita cambiando su fecha y hora de inicio.
     *
     * @param id           Identificador de la cita.
     * @param newStartTime Nueva fecha y hora de inicio.
     * @return La entidad {@link Appointment} actualizada.
     */
    @Transactional
    public Appointment rescheduleAppointment(Long id, LocalDateTime newStartTime) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + id));

        int durationMinutes = 60;
        LocalDateTime newEndTime = newStartTime.plusMinutes(durationMinutes);

        // Verificar solapamientos excluyendo la cita actual
        List<Appointment> overlapping = appointmentRepository.findByStartTimeBetween(newStartTime, newEndTime);
        for (Appointment app : overlapping) {
            if (!app.getId().equals(id) && app.getStatus() != AppointmentStatus.CANCELLED) {
                throw new IllegalArgumentException("El nuevo hueco seleccionado ya no está disponible.");
            }
        }

        appointment.setStartTime(newStartTime);
        appointment.setEndTime(newEndTime);
        return appointmentRepository.save(appointment);
    }

}