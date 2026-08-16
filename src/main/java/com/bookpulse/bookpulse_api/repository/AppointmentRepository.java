package com.bookpulse.bookpulse_api.repository;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
}