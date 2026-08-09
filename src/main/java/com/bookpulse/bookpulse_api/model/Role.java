package com.bookpulse.bookpulse_api.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Define los roles de acceso y permisos disponibles en el sistema.
 * @author Cristian
 */
public enum Role {
    ROLE_CLIENT,
    ROLE_ADMIN;

    @JsonCreator
    public static Role fromString(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim().toUpperCase();

        // Si el cliente lo envía sin el prefijo "ROLE_", se lo añadimos
        if (!normalized.startsWith("ROLE_")) {
            normalized = "ROLE_" + normalized;
        }

        for (Role role : Role.values()) {
            if (role.name().equals(normalized)) {
                return role;
            }
        }

        throw new IllegalArgumentException("Rol no válido: " + value);
    }
}