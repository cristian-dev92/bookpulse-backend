package com.bookpulse.bookpulse_api.repository;

import com.bookpulse.bookpulse_api.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio de acceso a datos para la gestión de usuarios.
 * * @author Cristian
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Busca un usuario en la base de datos a través de su correo electrónico.
     *
     * @param email Correo del usuario.
     * @return Un {@link Optional} que contiene al usuario si existe.
     */
    Optional<User> findByEmail(String email);
}