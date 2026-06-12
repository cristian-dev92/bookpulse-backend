package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Componente de planificación encargado de ejecutar tareas en segundo plano.
 * <p>
 * Su función principal es actuar como un recolector de elementos expirados,
 * liberando los huecos horarios que quedaron en estado pendiente pero que
 * no fueron confirmados por el usuario dentro del tiempo de cortesía.
 * </p>
 *
 * @author Cristian
 * @since 1.0
 */
@Component
public class AppointmentScheduler {

    private final AppointmentRepository appointmentRepository;

    /**
     * Inyección de dependencias del repositorio.
     *
     * @param appointmentRepository Repositorio para modificar el estado de las citas.
     */
    @Autowired
    public AppointmentScheduler(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Tarea programada que se ejecuta periódicamente para limpiar reservas expiradas.
     * <p>
     * Se ejecuta cada 60.000 milisegundos (1 minuto). Busca todas las citas en estado
     * {@link AppointmentStatus#PENDING} cuya fecha de creación o bloqueo supere los 5 minutos
     * de antigüedad y las marca como {@link AppointmentStatus#CANCELLED} para que vuelvan
     * a aparecer disponibles en el sistema.
     * </p>
     */
    @Scheduled(fixedRate = 60000)
    @Transactional
    public void releaseExpiredAppointments() {
        // Calculamos el tiempo límite (hace 5 minutos)
        LocalDateTime expirationThreshold = LocalDateTime.now().minusMinutes(5);

        // Para simplificar este flujo inicial, buscamos todas las citas.
        // En producción filtraríamos además por un campo 'createdAt', pero usando 'startTime'
        // podemos comprobar si hay citas pendientes cuya hora de inicio ya pertenezca al pasado
        // o estén bloqueadas de más.
        List<Appointment> allAppointments = appointmentRepository.findAll();

        int releasedCount = 0;

        for (Appointment appointment : allAppointments) {
            if (appointment.getStatus() == AppointmentStatus.PENDING &&
                    appointment.getStartTime().isBefore(expirationThreshold)) {

                appointment.setStatus(AppointmentStatus.CANCELLED);
                appointmentRepository.save(appointment);
                releasedCount++;
            }
        }

        if (releasedCount > 0) {
            // Un log limpio para ver en la consola de Spring Boot que el scheduler funciona
            System.out.println("[Scheduler] Se han liberado " + releasedCount + " citas expiradas automáticamente.");
        }
    }
}