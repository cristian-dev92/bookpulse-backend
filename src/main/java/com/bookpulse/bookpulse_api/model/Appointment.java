package com.bookpulse.bookpulse_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * Entidad que representa una cita o reserva en el sistema.
 * <p>
 * Implementa un mecanismo de control de concurrencia optimista mediante el uso
 * de la anotación {@link Version} para evitar que dos usuarios reserven
 * el mismo hueco horario simultáneamente.
 * </p>
 *
 * @author Cristian
 * @since 2026
 */
@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {

    /** Identificador único autoincremental de la cita. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Fecha y hora exacta de inicio de la cita. */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** Fecha y hora exacta de finalización de la cita. */
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    /** Estado actual en el que se encuentra la reserva. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AppointmentStatus status;

    /** Cliente que realiza la reserva. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /** Servicio contratado para esta cita. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    /** Precio fijado en el momento de crear la cita. */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    /** Estado del pago asociado a la cita (PENDING o PAID). */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** Identificador de la sesión de Stripe Checkout vinculada a la cita (si procede). */
    @Column(name = "stripe_session_id", length = 255)
    private String stripeSessionId;

    /** Fecha y hora de creación del registro (para expirar reservas no pagadas). */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Indica si el recordatorio automático de esta cita ya fue enviado.
     *  Control de envío único: impide que la tarea programada reenvíe el aviso
     *  y agote las cuotas de Resend/Twilio. Por defecto {@code false}. */
    @Column(name = "reminder_sent", nullable = false, columnDefinition = "boolean default false")
    private boolean reminderSent = false;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    /** Notas u observaciones adicionales. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    /**
     * Campo de control de versión para el bloqueo optimista (Optimistic Locking).
     * <p>
     * Hibernate incrementa automáticamente este valor en cada actualización.
     * Si dos transacciones intentan modificar la misma cita a la vez, la segunda
     * fallará lanzando una excepción de concurrencia.
     * </p>
     */
    @Version
    private Long version;
}
