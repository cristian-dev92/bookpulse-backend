package com.bookpulse.bookpulse_api.model;

/**
 * Representa los estados posibles en los que se puede encontrar una cita
 * dentro del sistema BookPulse.
 * * @author Cristian
 * @version 1.0
 */
public enum AppointmentStatus {
    /** El hueco está libre y disponible para ser reservado por un cliente. */
    AVAILABLE,

    /** El hueco ha sido seleccionado por un usuario y está bloqueado temporalmente. */
    PENDING,

    /** La cita ha sido confirmada y asociada definitivamente a un cliente. */
    CONFIRMED,

    /** La cita ha sido cancelada por el negocio o por el cliente. */
    CANCELLED
}