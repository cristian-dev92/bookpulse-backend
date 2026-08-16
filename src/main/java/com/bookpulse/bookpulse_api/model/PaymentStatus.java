package com.bookpulse.bookpulse_api.model;

/**
 * Representa el estado del pago asociado a una cita de BookPulse.
 *
 * @author Cristian
 */
public enum PaymentStatus {
    /** El pago aún no se ha realizado (estado por defecto al reservar). */
    PENDING,

    /** El pago ha sido completado con éxito. */
    PAID
}