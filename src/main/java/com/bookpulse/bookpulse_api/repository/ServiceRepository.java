package com.bookpulse.bookpulse_api.repository;

import com.bookpulse.bookpulse_api.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio de acceso a datos para la entidad {@link Service}.
 * Proporciona los métodos CRUD básicos y consultas personalizadas para los servicios ofertados.
 *
 * @author Cristian
 * @since 1.0
 */
@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    /**
     * Recupera únicamente los servicios que están activos en la plataforma.
     * Útil para mostrar al cliente final solo lo que se puede reservar.
     *
     * @return Lista de servicios activos.
     */
    List<Service> findByActiveTrue();
}