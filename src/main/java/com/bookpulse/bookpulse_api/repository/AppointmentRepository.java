package com.bookpulse.bookpulse_api.repository;

import com.bookpulse.bookpulse_api.model.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
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
}