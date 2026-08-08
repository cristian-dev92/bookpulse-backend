package com.bookpulse.bookpulse_api.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

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

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

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
