package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Servicio de integración con la API de Stripe (modo prueba).
 * <p>
 * Encapsula la creación de sesiones de Stripe Checkout y la verificación de
 * eventos (webhook). La clave de API se lee desde el entorno
 * ({@code stripe.api.key}) y solo se activa si está presente; sin ella el
 * {@link PaymentService} trabaja en modo simulado para poder probar el flujo
 * de reserva en desarrollo.
 * </p>
 *
 * @author Cristian
 */
@Service
public class StripeService {

    private final String apiKey;
    private final String webhookSecret;

    public StripeService(@Value("${stripe.api.key:}") String apiKey,
                         @Value("${stripe.webhook.secret:}") String webhookSecret) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    public void init() {
        if (apiKey != null && !apiKey.isBlank()) {
            Stripe.apiKey = apiKey;
        }
    }

    /** Indica si hay una clave de Stripe configurada (modo real) o se usa el modo simulado. */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Crea una sesión de Stripe Checkout de pago único (tarjeta) para la cita.
     *
     * @param appointment Cita cuyo precio se cobra.
     * @param successUrl  URL de retorno tras el pago correcto.
     * @param cancelUrl   URL de retorno si el cliente abandona el pago.
     * @return La sesión de Checkout creada en Stripe.
     * @throws StripeException Si Stripe rechaza la operación.
     */
    public Session createCheckoutSession(Appointment appointment, String successUrl, String cancelUrl) throws StripeException {
        String serviceName = appointment.getService() != null && appointment.getService().getName() != null
                ? appointment.getService().getName()
                : "Servicio BookPulse";
        BigDecimal price = appointment.getPrice() != null ? appointment.getPrice() : BigDecimal.ZERO;
        long amountCents = price.movePointRight(2).longValueExact();

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(String.valueOf(appointment.getId()))
                .putAllMetadata(Map.of("appointmentId", String.valueOf(appointment.getId())))
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("eur")
                                .setUnitAmount(amountCents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("BookPulse - " + serviceName)
                                        .build())
                                .build())
                        .build())
                .build();

        return Session.create(params);
    }

    /**
     * Recupera una sesión de Checkout para verificar su estado de pago.
     *
     * @param sessionId Identificador de la sesión.
     * @return La sesión de Stripe.
     * @throws StripeException Si Stripe no puede resolver la sesión.
     */
    public Session retrieveSession(String sessionId) throws StripeException {
        return Session.retrieve(sessionId);
    }

    /**
     * Ejecuta el reembolso oficial de una sesión de Stripe Checkout ya pagada.
     * <p>
     * Recupera la sesión, extrae el {@code payment_intent} asociado y crea un
     * {@code Refund} por el importe total cobrado. Si el pago ya fue devuelto
     * previamente, Stripe rechaza la operación y se lanza {@link StripeException},
     * que el llamador debe capturar sin bloquear el flujo de cancelación.
     * </p>
     *
     * @param stripeSessionId Identificador de la sesión de Checkout.
     * @return El {@code Refund} creado en Stripe.
     * @throws StripeException Si Stripe rechaza el reembolso (p. ej. ya devuelto).
     */
    public Refund refundPayment(String stripeSessionId) throws StripeException {
        if (!isConfigured()) {
            System.out.println("[StripeService] Modo simulado: se omite el reembolso de la sesión " + stripeSessionId);
            return null;
        }

        Session session = retrieveSession(stripeSessionId);
        String paymentIntentId = session.getPaymentIntent();
        if (paymentIntentId == null || paymentIntentId.isBlank()) {
            throw new StripeException("La sesión de Checkout no tiene un Payment Intent asociado", null, null, null) {};
        }

        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .build();

        Refund refund = Refund.create(params);
        System.out.println("[StripeService] Reembolso " + refund.getId() + " creado (sesión " + stripeSessionId
                + ", estado " + refund.getStatus() + ")");
        return refund;
    }

    /**
     * Construye y valida un evento de webhook de Stripe.
     * <p>
     * Si no hay {@code stripe.webhook.secret} configurado (entorno dev), se omite
     * la verificación de firma para poder probar con peticiones manuales.
     * </p>
     *
     * @param payload   Cuerpo crudo de la petición del webhook.
     * @param sigHeader Cabecera {@code Stripe-Signature}.
     * @return El evento de Stripe.
     * @throws SignatureVerificationException Si la firma no es válida.
     */
    public Event constructWebhookEvent(String payload, String sigHeader) throws SignatureVerificationException {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            System.out.println("[StripeService] Sin STRIPE_WEBHOOK_SECRET: se omite la verificación de firma del webhook (dev).");
            return Event.GSON.fromJson(payload, Event.class);
        }
        return Webhook.constructEvent(payload, sigHeader, webhookSecret);
    }
}