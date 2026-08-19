package com.bookpulse.bookpulse_api.model;

/**
 * Representa el estado del pago asociado a una cita de BookPulse.
 *
 * @author Cristian
 */
public enum PaymentStatus {
    /** El pago aún no se ha realizado (estado por defecto al reservar). */
    PENDING,

    /** El pago no se ha completado (p. ej. sesión de checkout abandonada o fallida). */
    UNPAID,

    /** El pago ha sido completado con éxito. */
    PAID,

    /** El importe ha sido devuelto al cliente tras cancelar la cita. */
    REFUNDED
}