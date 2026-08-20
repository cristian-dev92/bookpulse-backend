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
 * Componente de planificación (cron) encargado de las tareas automáticas del sistema.
 * <p>
 * Sustituye al antiguo {@code AppointmentScheduler} unificando toda la lógica de
 * mantenimiento en un único punto:
 * <ul>
 *   <li><strong>Limpieza de reservas expiradas:</strong> libera los huecos que quedaron
 *       bloqueados sin pago/confirmación dentro del tiempo de cortesía.</li>
 *   <li><strong>Recordatorios automáticos:</strong> avisa al cliente de sus citas
 *       próximas con <strong>control de envío único</strong> (flag {@code reminderSent})
 *       para no agotar las cuotas de Resend/Twilio.</li>
 * </ul>
 * Todas las tareas usan UPDATE masivos atómicos (ignoran {@code @Version}) para que
 * no entren en conflicto con las operaciones concurrentes de reservas o el webhook
 * de Stripe.
 * </p>
 *
 * @author Cristian
 * @since 1.1
 */
@Component
public class AppointmentTaskService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;

    @Autowired
    public AppointmentTaskService(AppointmentRepository appointmentRepository,
                                  AppointmentService appointmentService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
    }

    /**
     * Limpieza de reservas expiradas sin pagar o confirmar.
     * <p>
     * Se ejecuta cada 15 minutos (900.000 ms):
     * <ol>
     *   <li>Las citas en {@code PENDING} creadas hace más de 10 minutos sin confirmar
     *       ni pagar se marcan como {@code CANCELLED}, liberando la hora en el calendario.</li>
     *   <li>Las citas en {@code PENDING_PAYMENT} creadas hace más de 15 minutos sin
     *       completar el pago en Stripe Checkout también se marcan como {@code CANCELLED}.</li>
     * </ol>
     * </p>
     */
    @Scheduled(fixedRate = 900000)
    @Transactional
    public void cleanupExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();

        // 1. PENDING con cortesía de 10 minutos (el usuario no confirmó ni pagó)
        LocalDateTime pendingCutoff = now.minusMinutes(10);
        int releasedPending = appointmentRepository.cancelExpiredPending(
                AppointmentStatus.PENDING,
                AppointmentStatus.CANCELLED,
                pendingCutoff
        );

        // 2. PENDING_PAYMENT con 15 minutos para completar el pago en Checkout
        LocalDateTime paymentCutoff = now.minusMinutes(15);
        int releasedPayments = appointmentRepository.cancelExpiredPendingPayments(
                AppointmentStatus.PENDING_PAYMENT,
                AppointmentStatus.CANCELLED,
                paymentCutoff
        );

        int total = releasedPending + releasedPayments;
        if (total > 0) {
            System.out.println("[Scheduler] Limpieza completada: " + total
                    + " reserva(s) expirada(s) liberada(s) ("
                    + releasedPending + " PENDING, " + releasedPayments + " PENDING_PAYMENT).");
        }
    }

    /**
     * Recordatorios automáticos de citas próximas.
     * <p>
     * Se ejecuta cada 30 minutos (cron). Busca únicamente citas en estado
     * {@code CONFIRMED} programadas para las próximas 24 horas y con
     * {@code reminderSent == false}. Para cada una, encola la notificación
     * (email vía Resend + WhatsApp/SMS vía Twilio) y marca inmediatamente
     * {@code reminderSent = true} en la misma transacción.
     * </p>
     * <p>
     * El flag {@code reminderSent} garantiza el <strong>control de envío único</strong>:
     * aunque el cron se ejecute de nuevo antes de la cita, la cita ya no vuelve a
     * aparecer en la consulta y no se reenvía ni se agotan las cuotas.
     * </p>
     */
    @Scheduled(cron = "0 0/30 * * * *")
    @Transactional
    public void sendUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> upcoming = appointmentRepository.findUpcomingForReminder(
                AppointmentStatus.CONFIRMED,
                now,
                now.plusHours(24)
        );

        if (upcoming.isEmpty()) {
            return;
        }

        int sentCount = 0;
        for (Appointment appointment : upcoming) {
            try {
                // Encola los envíos asíncronos (email + WhatsApp/SMS)
                appointmentService.sendReminder(appointment);

                // Control de envío único: se marca ANTES de confirmar la transacción
                // para que ninguna ejecución posterior del cron vuelva a notificar.
                appointment.setReminderSent(true);
                appointmentRepository.save(appointment);
                sentCount++;
            } catch (Exception e) {
                System.err.println("[Scheduler] No se pudo procesar el recordatorio de la cita #"
                        + appointment.getId() + ": " + e.getMessage());
            }
        }

        if (sentCount > 0) {
            System.out.println("[Scheduler] Recordatorios enviados para " + sentCount
                    + " cita(s) en las próximas 24h.");
        }
    }
}