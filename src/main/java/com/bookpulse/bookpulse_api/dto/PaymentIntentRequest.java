package com.bookpulse.bookpulse_api.dto;

/**
 * Petición para crear un Payment Intent de una cita concreta.
 *
 * @param appointmentId Identificador de la cita a pagar.
 */
public record PaymentIntentRequest(Long appointmentId) {
}