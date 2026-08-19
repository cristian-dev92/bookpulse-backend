package com.bookpulse.bookpulse_api.dto;

/**
 * Respuesta del endpoint de creación de la sesión de Stripe Checkout.
 *
 * @param checkoutUrl URL de Stripe a la que se redirige al usuario para pagar.
 * @param sessionId   Identificador de la sesión de Checkout creada.
 * @param mock        {@code true} si se generó una URL ficticia (modo dev sin STRIPE_SECRET_KEY).
 */
public record CheckoutSessionResponse(String checkoutUrl, String sessionId, boolean mock) {
}