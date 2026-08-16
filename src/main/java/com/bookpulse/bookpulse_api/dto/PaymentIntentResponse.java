package com.bookpulse.bookpulse_api.dto;

import java.math.BigDecimal;

/**
 * Respuesta del endpoint de creación de un Payment Intent (Stripe).
 * <p>
 * En entorno de prueba (sin STRIPE_SECRET_KEY configurada) devuelve un
 * clientSecret ficticio para poder conectar Stripe Elements en el frontend.
 * </p>
 *
 * @param clientSecret Identificador para que Stripe Elements complete el pago.
 * @param amount        Importe en euros de la reserva (ya mostrado en UI).
 * @param currency      Moneda (eur).
 * @param mock          {@code true} si el clientSecret es ficticio (modo preparación).
 */
public record PaymentIntentResponse(String clientSecret, BigDecimal amount, String currency, boolean mock) {
}