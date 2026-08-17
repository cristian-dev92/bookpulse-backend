package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.AppointmentRescheduleDTO;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.PaymentStatus;
import com.bookpulse.bookpulse_api.model.Role;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import com.bookpulse.bookpulse_api.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
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
    private final ServiceRepository serviceRepository;
    private final EmailService emailService;

    /**
     * Inyección de dependencias a través del constructor.
     *
     * @param appointmentRepository Repositorio de citas.
     * @param timeSlotService       Servicio de cálculo de franjas horarias.
     * @param serviceRepository     Repositorio de servicios.
     * @param emailService          Servicio de notificaciones por correo.
     */
    @Autowired
    public AppointmentService(AppointmentRepository appointmentRepository, TimeSlotService timeSlotService, ServiceRepository serviceRepository, EmailService emailService) {
        this.appointmentRepository = appointmentRepository;
        this.timeSlotService = timeSlotService;
        this.serviceRepository = serviceRepository;
        this.emailService = emailService;
    }

    /**
     * Obtiene los huecos horarios que están libres para un día determinado.
     * <p>
     * Este método define una jornada laboral estándar ficticia (por ahora) de 09:00 a 18:00
     * con servicios de 60 minutos. Busca las citas existentes en la BD para ese día
     * y le pide al motor de tiempos que calcule los huecos disponibles.
     * </p>
     *
     * @param date                El día que se desea consultar.
     * @param excludeAppointmentId Si se está reprogramando una cita, su ID no se computa
     *                             como conflicto para que su hora actual aparezca disponible (opcional).
     * @return Una lista ordenada de {@link LocalDateTime} con los inicios de cada turno libre.
     */
    @Transactional(readOnly = true)
    public List<LocalDateTime> getAvailableSlotsForDay(LocalDate date, Long excludeAppointmentId) {
        // Definimos el rango del día completo (desde las 00:00:00 hasta las 23:59:59)
        LocalDateTime startOfDay = date.atStartOfDay();
        LocalDateTime endOfDay = date.atTime(LocalTime.MAX);

        // Pasos intermedios profesionales:
        // 1. Recuperamos las citas que ya existan ese día en la BD
        List<Appointment> existingAppointments = appointmentRepository.findByStartTimeBetween(startOfDay, endOfDay);

        // 2. Si se indica una cita en concreto (reprogramación), no se considera conflicto
        if (excludeAppointmentId != null) {
            existingAppointments = existingAppointments.stream()
                    .filter(a -> !a.getId().equals(excludeAppointmentId))
                    .toList();
        }

        // 3. Parámetros de negocio (Hardcodeados provisionalmente para las pruebas)
        LocalTime workStart = LocalTime.of(9, 0);
        LocalTime workEnd = LocalTime.of(18, 0);
        int durationMinutes = 60; // Duración base para la rejilla de slots

        // 4. Delegamos el cálculo algorítmico al motor de tiempos y garantizamos orden cronológico
        return timeSlotService.generateAvailableSlots(date, workStart, workEnd, durationMinutes, existingAppointments)
                .stream()
                .sorted()
                .toList();
    }

    /**
     * Inicia el proceso de reserva de un hueco horario, poniéndolo en estado temporal.
     * <p>
     * Al estar anotado con {@link Transactional}, si salta un problema de concurrencia
     * (bloqueo optimista) o el hueco ya se ocupó, la base de datos hará un rollback automático.
     * </p>
     *
     * @param startTime Fechay hora de inicio deseada.
     * @param user      Usuario que realiza la reserva.
     * @param serviceId ID del servicio a contratar.
     * @return La entidad {@link Appointment} guardada en estado PENDING.
     */
    @Transactional
    public Appointment reserveSlot(LocalDateTime startTime, User user, Long serviceId) {
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se pueden reservar citas en el pasado.");
        }
        // 1. Obtener el servicio y validar existencia
        com.bookpulse.bookpulse_api.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró el servicio con ID: " + serviceId));

        // 2. Calcular fecha fin según la duración real del servicio
        LocalDateTime endTime = startTime.plusMinutes(service.getDurationMinutes());

        // 3. Comprobar si hay solapamientos con citas confirmadas o pendientes
        List<AppointmentStatus> excludedStatuses = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.AVAILABLE);
        boolean isOverlapping = appointmentRepository.existsOverlappingAppointment(startTime, endTime, excludedStatuses);

        if (isOverlapping) {
            throw new IllegalArgumentException("El hueco seleccionado ya no está disponible.");
        }

        // Creamos la nueva cita en estado PENDING (bloqueo de cortesía de 5 minutos)
        Appointment newAppointment = new Appointment();
        newAppointment.setStartTime(startTime);
        newAppointment.setEndTime(endTime);
        newAppointment.setStatus(AppointmentStatus.PENDING);
        newAppointment.setPaymentStatus(PaymentStatus.PENDING);
        newAppointment.setUser(user);
        newAppointment.setService(service);
        newAppointment.setPrice(service.getPrice()); // Fijamos el precio actual del servicio

        // Al guardar, Hibernate gestiona el campo @Version automáticamente
        Appointment saved = appointmentRepository.save(newAppointment);

        // Notificación por email con el resumen de la cita
        emailService.sendAppointmentConfirmation(saved);

        return saved;
    }

    /**
     * Recupera de forma paginada todas las citas del sistema (Admin).
     * Opcionalmente filtra por un estado concreto.
     *
     * @param status   Estado por el que filtrar (opcional, null = todas).
     * @param pageable Paginación y orden.
     * @return Una página con todas las entidades {@link Appointment}.
     */
    @Transactional(readOnly = true)
    public Page<Appointment> getAllAppointments(AppointmentStatus status, Pageable pageable) {
        if (status == null) {
            return appointmentRepository.findAll(pageable);
        }
        return appointmentRepository.findByStatus(status, pageable);
    }

    /**
     * Recupera las citas activas dentro de un rango de fechas (Admin).
     * Útil para alimentar el calendario global del panel de administración.
     * <p>
     * Solo se devuelven citas en estado PENDING, CONFIRMED o COMPLETED:
     * las canceladas o no presentadas no ocupan hueco y no deben aparecer como
     * "fantasmas" en el calendario.
     * </p>
     *
     * @param from Inicio del rango (inclusive).
     * @param to   Fin del rango (inclusive).
     * @return Una lista de {@link Appointment} activas dentro del rango.
     */
    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsBetween(LocalDateTime from, LocalDateTime to) {
        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.PENDING,
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.COMPLETED
        );
        return appointmentRepository.findByStartTimeBetween(from, to).stream()
                .filter(a -> activeStatuses.contains(a.getStatus()))
                .toList();
    }

    /**
     * Cancela una cita cambiando su estado a CANCELLED.
     * Solo el propietario de la cita o un administrador pueden cancelarla, y únicamente
     * cuando la cita está en estado PENDING o CONFIRMED.
     *
     * @param id          Identificador de la cita a cancelar.
     * @param currentUser Usuario autenticado que realiza la petición.
     * @return La entidad {@link Appointment} actualizada.
     */
    @Transactional
    public Appointment cancelAppointment(Long id, User currentUser) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + id));

        checkOwnershipOrAdmin(appointment, currentUser, "cancelar esta cita");

        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.COMPLETED
                || status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.NO_SHOW) {
            throw new IllegalArgumentException(
                    "No se puede cancelar una cita en estado " + status + ".");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);
        return appointmentRepository.save(appointment);
    }

    /**
     * Reprograma una cita cambiando su fecha y hora de inicio y, opcionalmente, el servicio.
     * Solo el propietario de la cita o un administrador pueden reprogramarla.
     * Regla de negocio: únicamente las citas en estado PENDING pueden reprogramarse;
     * las confirmadas deben cancelarse primero o contactar con administración.
     *
     * @param id          Identificador de la cita.
     * @param dto         DTO con la nueva fecha/hora y el servicio opcional.
     * @param currentUser Usuario autenticado que realiza la petición.
     * @return La entidad {@link Appointment} actualizada.
     */
    @Transactional
    public Appointment rescheduleAppointment(Long id, AppointmentRescheduleDTO dto, User currentUser) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + id));

        checkOwnershipOrAdmin(appointment, currentUser, "reprogramar esta cita");

        // Regla de negocio: solo las citas PENDING pueden reprogramarse
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Esta cita está en estado " + appointment.getStatus()
                            + " y no puede reprogramarse. Las citas confirmadas deben cancelarse o contactar con administración.");
        }

        LocalDateTime newStartTime = dto.getNewDateTime();
        if (newStartTime == null) {
            throw new IllegalArgumentException("La nueva fecha y hora de inicio es obligatoria.");
        }
        if (newStartTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("No se pueden programar citas en el pasado.");
        }

        // Cambio de servicio opcional: recalcula duración y precio
        if (dto.getServiceId() != null) {
            com.bookpulse.bookpulse_api.model.Service service = serviceRepository.findById(dto.getServiceId())
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró el servicio con ID: " + dto.getServiceId()));
            appointment.setService(service);
            appointment.setPrice(service.getPrice());
        }

        // Mantener la duración del servicio asociado o 60 min por defecto
        int durationMinutes = (appointment.getService() != null)
                ? appointment.getService().getDurationMinutes()
                : 60;
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

    @Transactional
    public Appointment updateAppointmentStatus(Long id, AppointmentStatus status, User currentUser) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cita no encontrada"));

        checkOwnershipOrAdmin(appointment, currentUser, "cambiar el estado de esta cita");

        // UPDATE directo sobre la columna status (sin tocar User, Service ni cascadas)
        appointmentRepository.updateStatusOnly(id, status);
        appointment.setStatus(status);

        // Si la cita pasa a CONFIRMED, enviamos el correo de confirmación
        if (status == AppointmentStatus.CONFIRMED) {
            emailService.sendAppointmentConfirmation(appointment);
        }

        return appointment;
    }

    @Transactional(readOnly = true)
    public Page<Appointment> getMyAppointments(Long userId, List<AppointmentStatus> statuses, Pageable pageable) {
        if (statuses == null || statuses.isEmpty()) {
            return appointmentRepository.findByUserId(userId, pageable);
        }
        return appointmentRepository.findByUserIdAndStatusIn(userId, statuses, pageable);
    }

    /**
     * Comprueba que el usuario autenticado sea el propietario de la cita o un administrador.
     * Si no cumple ninguna de las dos condiciones, lanza {@link AccessDeniedException}.
     *
     * @param appointment La cita sobre la que se quiere actuar.
     * @param currentUser El usuario autenticado que realiza la petición.
     * @param action      Descripción de la operación que se intenta realizar.
     */
    private void checkOwnershipOrAdmin(Appointment appointment, User currentUser, String action) {
        boolean isAdmin = currentUser != null && currentUser.getRole() == Role.ROLE_ADMIN;
        boolean isOwner = currentUser != null
                && appointment.getUser() != null
                && appointment.getUser().getId().equals(currentUser.getId());

        if (!isAdmin && !isOwner) {
            throw new AccessDeniedException("No tienes permisos para " + action);
        }
    }

}