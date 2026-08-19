package com.bookpulse.bookpulse_api.dto;

/**
 * Petición para confirmar un pago tras el redireccionamiento de Stripe Checkout.
 *
 * @param sessionId Identificador de la sesión de Checkout.
 */
public record PaymentConfirmRequest(String sessionId) {
}