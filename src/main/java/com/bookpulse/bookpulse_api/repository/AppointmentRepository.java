package com.bookpulse.bookpulse_api.repository;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio de acceso a datos para la entidad {@link Appointment}.
 * Proporciona los métodos necesarios para interactuar con la tabla de citas en PostgreSQL.
 *
 * @author Cristian
 */
@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Recupera todas las citas comprendidas en un rango de fechas determinado.
     * Útil para comprobar la disponibilidad de un día concreto.
     *
     * @param start Fecha de inicio del rango.
     * @param end   Fecha de fin del rango.
     * @return Una lista de {@link Appointment} encontradas en ese intervalo.
     */
    List<Appointment> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Recupera todas las citas asociadas a un usuario específico.
     *
     * @param userId ID del usuario.
     * @return Una lista de {@link Appointment} pertenecientes al usuario.
     */
    List<Appointment> findByUserId(Long userId);

    /**
     * Recupera de forma paginada las citas de un usuario.
     *
     * @param userId   ID del usuario.
     * @param pageable Paginación y orden.
     * @return Una página de {@link Appointment} pertenecientes al usuario.
     */
    Page<Appointment> findByUserId(Long userId, Pageable pageable);

    /**
     * Recupera de forma paginada las citas de un usuario filtrando por uno o varios estados.
     *
     * @param userId    ID del usuario.
     * @param statuses  Lista de estados permitidos (p. ej. PENDING, CONFIRMED).
     * @param pageable  Paginación y orden.
     * @return Una página de {@link Appointment} del usuario en los estados indicados.
     */
    Page<Appointment> findByUserIdAndStatusIn(Long userId, List<AppointmentStatus> statuses, Pageable pageable);

    /**
     * Recupera de forma paginada las citas que están en un estado concreto.
     *
     * @param status   Estado de la cita (p. ej. CONFIRMED).
     * @param pageable Paginación y orden.
     * @return Una página de {@link Appointment} en el estado indicado.
     */
    Page<Appointment> findByStatus(AppointmentStatus status, Pageable pageable);

    /**
     * Cuenta cuántas citas referencian un servicio concreto.
     * <p>
     * Se usa para impedir el borrado físico de un servicio que ya tiene historial
     * de citas, evitando violaciones de integridad referencial en la BD.
     * </p>
     *
     * @param serviceId ID del servicio.
     * @return Número de citas que lo referencian.
     */
    long countByServiceId(Long serviceId);

    /**
     * Comprueba si existe alguna cita activa que ocupe o se solape con el rango horaria dado.
     * Ignora las citas que estén canceladas o disponibles.
     */
    @Query("""
        SELECT COUNT(a) > 0 FROM Appointment a\s
        WHERE a.status NOT IN (:excludedStatuses)
        AND (a.startTime < :newEndTime AND a.endTime > :newStartTime)
   \s""")
    boolean existsOverlappingAppointment(
            @Param("newStartTime") LocalDateTime newStartTime,
            @Param("newEndTime") LocalDateTime newEndTime,
            @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses
    );

    /**
     * Actualiza ÚNICAMENTE la columna {@code status} de la cita.
     * Usa una sentencia UPDATE directa de JPQL para evitar cualquier cascada o
     * re-merge de entidades asociadas (User, Service) durante el guardado.
     *
     * @param id     Identificador de la cita.
     * @param status Nuevo estado a aplicar.
     * @return Número de filas actualizadas.
     */
    @Modifying
    @Query("UPDATE Appointment a SET a.status = :status WHERE a.id = :id")
    int updateStatusOnly(@Param("id") Long id, @Param("status") AppointmentStatus status);

    /**
     * Recupera una cita con sus relaciones {@code user} y {@code service} ya cargadas
     * (JOIN FETCH), de modo que se pueda acceder a ellas fuera de la sesión de Hibernate.
     * <p>
     * Se usa desde el hilo asíncrono del {@code EmailService} para construir los
     * correos sin {@code LazyInitializationException}.
     * </p>
     *
     * @param id Identificador de la cita.
     * @return Un {@link Optional} con la cita poblada y sus relaciones, o vacío si no existe.
     */
    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.user u
        JOIN FETCH a.service s
        WHERE a.id = :id
    """)
    Optional<Appointment> findByIdWithRelations(@Param("id") Long id);

    /**
     * Recupera la cita asociada a una sesión de Stripe Checkout concreta.
     * <p>
     * Se usa tanto en el endpoint de confirmación (tras el redirect de Checkout)
     * como en el webhook {@code checkout.session.completed}.
     * </p>
     *
     * @param sessionId Identificador de la sesión de Stripe.
     * @return Un {@link Optional} con la cita, o vacío si no existe.
     */
    Optional<Appointment> findByStripeSessionId(String sessionId);

    /**
     * Actualiza de forma atómica una cita pendiente de pago a CONFIRMED + PAID.
     * <p>
     * Usa un UPDATE masivo que <strong>ignora el control de versión optimista</strong>
     * ({@code @Version}): si el webhook de Stripe y el endpoint /confirm confirman
     * la misma cita a la vez, solo el primero actualiza filas (retorno 1) y el
     * segundo obtiene 0 sin lanzar {@code ObjectOptimisticLockingFailureException}.
     * Así la confirmación es idempotente y libre de carreras.
     * </p>
     *
     * @param id              Identificador de la cita.
     * @param status          Estado objetivo (CONFIRMED).
     * @param paymentStatus   Estado de pago objetivo (PAID).
     * @param alreadyPaid     Valor que impide reprocesar una cita ya pagada.
     * @param expectedStatuses Estados de partida permitidos (PENDING o PENDING_PAYMENT).
     * @return Número de filas actualizadas (0 si la cita ya estaba pagada o no era pagable).
     */
    @Modifying
    @Query("""
        UPDATE Appointment a
        SET a.status = :status, a.paymentStatus = :paymentStatus
        WHERE a.id = :id
          AND a.paymentStatus <> :alreadyPaid
          AND a.status IN :expectedStatuses
    """)
    int confirmPaidIfPending(@Param("id") Long id,
                             @Param("status") AppointmentStatus status,
                             @Param("paymentStatus") PaymentStatus paymentStatus,
                             @Param("alreadyPaid") PaymentStatus alreadyPaid,
                             @Param("expectedStatuses") List<AppointmentStatus> expectedStatuses);

    /**
     * Libera (marca como CANCELLED) las citas que quedaron en {@code PENDING_PAYMENT}
     * sin completar el pago antes de una fecha límite.
     * <p>
     * Usa un UPDATE masivo atómico para que el cron y otras operaciones concurrentes
     * no generen conflictos de versión optimista. Se ignoran las citas sin
     * {@code createdAt} (filas históricas previas a la nueva columna).
     * </p>
     *
     * @param pendingPayment Estado de partida (PENDING_PAYMENT).
     * @param cancelled      Estado objetivo (CANCELLED).
     * @param cutoff         Momento límite: solo se liberan citas creadas antes de esta fecha.
     * @return Número de citas liberadas.
     */
    @Modifying
    @Query("""
        UPDATE Appointment a
        SET a.status = :cancelled
        WHERE a.status = :pendingPayment
          AND a.createdAt IS NOT NULL
          AND a.createdAt < :cutoff
    """)
    int cancelExpiredPendingPayments(@Param("pendingPayment") AppointmentStatus pendingPayment,
                                     @Param("cancelled") AppointmentStatus cancelled,
                                     @Param("cutoff") LocalDateTime cutoff);

    /**
     * Cancela (marca como CANCELLED) las citas en estado PENDING creadas antes de
     * la fecha límite sin que el cliente confirmara ni pagara.
     * <p>
     * Usa un UPDATE masivo atómico (ignora {@code @Version}) para que el cron y
     * otras operaciones concurrentes no generen conflictos de bloqueo optimista.
     * </p>
     *
     * @param pending   Estado de partida (PENDING).
     * @param cancelled Estado objetivo (CANCELLED).
     * @param cutoff    Momento límite: solo se liberan citas creadas antes de esta fecha.
     * @return Número de citas liberadas.
     */
    @Modifying
    @Query("""
        UPDATE Appointment a
        SET a.status = :cancelled
        WHERE a.status = :pending
          AND a.createdAt IS NOT NULL
          AND a.createdAt < :cutoff
    """)
    int cancelExpiredPending(@Param("pending") AppointmentStatus pending,
                             @Param("cancelled") AppointmentStatus cancelled,
                             @Param("cutoff") LocalDateTime cutoff);

    /**
     * Recupera las citas CONFIRMED programadas dentro de un rango de fechas que aún
     * no han recibido su recordatorio automático.
     * <p>
     * La condición {@code reminderSent = false} garantiza el control de envío único:
     * cada cita solo se notifica una vez, evitando agotar las cuotas de Resend/Twilio.
     * </p>
     *
     * @param status Estado requerido (CONFIRMED).
     * @param from   Inicio del rango (inclusive).
     * @param to     Fin del rango (inclusive).
     * @return Lista de citas pendientes de recordatorio.
     */
    @Query("""
        SELECT a FROM Appointment a
        WHERE a.status = :status
          AND a.startTime >= :from
          AND a.startTime <= :to
          AND a.reminderSent = false
    """)
    List<Appointment> findUpcomingForReminder(@Param("status") AppointmentStatus status,
                                              @Param("from") LocalDateTime from,
                                              @Param("to") LocalDateTime to);
}