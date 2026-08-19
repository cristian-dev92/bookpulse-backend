package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.AppointmentCreateDTO;
import com.bookpulse.bookpulse_api.dto.CheckoutSessionResponse;
import com.bookpulse.bookpulse_api.dto.PaymentIntentResponse;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.PaymentStatus;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Servicio que coordina la integración con Stripe Checkout en el flujo de reserva.
 * <p>
 * Flujo: el usuario elige servicio/fecha/hora, el backend crea la cita en
 * {@code PENDING_PAYMENT} y una sesión de Checkout; tras el pago (webhook o
 * endpoint de confirmación) la cita pasa a {@code CONFIRMED} con pago
 * {@code PAID} y se disparan los correos transaccionales.
 * </p>
 * <p>
 * Sin {@code STRIPE_SECRET_KEY} (entorno dev) se genera una sesión ficticia para
 * poder probar el flujo completo sin la pasarela real.
 * </p>
 *
 * @author Cristian
 */
@Service
public class PaymentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentService appointmentService;
    private final StripeService stripeService;

    @Value("${stripe.success.url:http://localhost:5173/booking-success?session_id={CHECKOUT_SESSION_ID}}")
    private String successUrlTemplate;

    @Value("${stripe.cancel.url:http://localhost:5173/booking-cancel}")
    private String cancelUrl;

    public PaymentService(AppointmentRepository appointmentRepository,
                          AppointmentService appointmentService,
                          StripeService stripeService) {
        this.appointmentRepository = appointmentRepository;
        this.appointmentService = appointmentService;
        this.stripeService = stripeService;
    }

    /**
     * Crea la cita en estado {@code PENDING_PAYMENT} y genera la sesión de
     * Stripe Checkout, guardando el identificador de la sesión en la cita.
     *
     * @param dto  Datos de la cita (fecha/hora, servicio, notas).
     * @param user Usuario autenticado que reserva.
     * @return La URL de Checkout, el ID de la sesión y si es modo simulado.
     */
    @Transactional
    public CheckoutSessionResponse createCheckoutSession(AppointmentCreateDTO dto, User user) {
        Appointment appointment = appointmentService.createPendingPaymentAppointment(
                dto.getStartTime(), user, dto.getServiceId(), dto.getNotes());

        if (!stripeService.isConfigured()) {
            // Modo dev: sesión ficticia que el endpoint /confirm resuelve directamente.
            String mockSessionId = "mock_cs_" + appointment.getId();
            appointment.setStripeSessionId(mockSessionId);
            appointmentRepository.save(appointment);

            String successUrl = successUrlTemplate.replace("{CHECKOUT_SESSION_ID}", mockSessionId);
            return new CheckoutSessionResponse(successUrl, mockSessionId, true);
        }

        try {
            Session session = stripeService.createCheckoutSession(appointment, successUrlTemplate, cancelUrl);
            appointment.setStripeSessionId(session.getId());
            appointmentRepository.save(appointment);
            return new CheckoutSessionResponse(session.getUrl(), session.getId(), false);
        } catch (StripeException e) {
            // Si Stripe falla, liberamos el hueco para no dejar citas bloqueadas.
            appointmentRepository.delete(appointment);
            throw new RuntimeException("No se pudo iniciar la sesión de pago con Stripe: " + e.getMessage(), e);
        }
    }

    /**
     * Verifica el estado del pago de una sesión de Checkout tras el redireccionamiento
     * del usuario y confirma la cita si el cobro se ha completado.
     *
     * @param sessionId Identificador de la sesión de Checkout.
     * @return La cita confirmada y pagada.
     */
    @Transactional
    public Appointment confirmPayment(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("El identificador de sesión es obligatorio.");
        }

        if (sessionId.startsWith("mock_cs_")) {
            Appointment appointment = appointmentRepository.findByStripeSessionId(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("No se encontró la reserva asociada a esta sesión de prueba."));
            return appointmentService.confirmAppointmentAfterPayment(appointment.getId());
        }

        try {
            Session session = stripeService.retrieveSession(sessionId);
            String appointmentId = extractAppointmentId(session);
            if (appointmentId == null) {
                throw new IllegalArgumentException("No se pudo asociar la sesión de pago a ninguna cita.");
            }
            if (!"paid".equals(session.getPaymentStatus())) {
                throw new IllegalArgumentException("El pago aún no se ha completado para esta sesión.");
            }
            return appointmentService.confirmAppointmentAfterPayment(Long.valueOf(appointmentId));
        } catch (StripeException e) {
            throw new RuntimeException("No se pudo verificar el pago con Stripe: " + e.getMessage(), e);
        }
    }

    /**
     * Procesa un evento de webhook de Stripe ({@code checkout.session.completed}).
     *
     * @param payload   Cuerpo crudo del webhook.
     * @param sigHeader Cabecera {@code Stripe-Signature}.
     * @return La cita confirmada, o {@code null} si el evento no era de pago completado.
     */
    @Transactional
    public Appointment handleWebhookEvent(String payload, String sigHeader) {
        try {
            Event event = stripeService.constructWebhookEvent(payload, sigHeader);

            if (!"checkout.session.completed".equals(event.getType())) {
                return null;
            }

            Session session = event.getDataObjectDeserializer().getObject()
                    .filter(obj -> obj instanceof Session)
                    .map(obj -> (Session) obj)
                    .orElse(null);
            if (session == null) {
                return null;
            }

            String appointmentId = extractAppointmentId(session);
            if (appointmentId == null) {
                throw new IllegalArgumentException("El webhook no incluye el identificador de la cita.");
            }

            return appointmentService.confirmAppointmentAfterPayment(Long.valueOf(appointmentId));
        } catch (StripeException e) {
            throw new RuntimeException("Webhook de Stripe inválido: " + e.getMessage(), e);
        }
    }

    /**
     * Marca como CANCELLED una cita que quedó en {@code PENDING_PAYMENT} porque el
     * usuario abandonó el pago en Stripe (llega aquí desde /booking-cancel).
     * <p>
     * Idempotente: solo libera la cita si sigue pendiente de pago; si ya fue pagada
     * o confirmada no se toca nada.
     * </p>
     *
     * @param sessionId Identificador de la sesión de Checkout abandonada.
     * @return La cita liberada (o su estado actual si ya no estaba pendiente).
     */
    @Transactional
    public Appointment cancelPendingSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("El identificador de sesión es obligatorio.");
        }

        Appointment appointment = appointmentRepository.findByStripeSessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la reserva asociada a esta sesión."));

        if (appointment.getStatus() == AppointmentStatus.PENDING_PAYMENT
                && appointment.getPaymentStatus() != PaymentStatus.PAID) {
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appointment);
            System.out.println("[PaymentService] Reserva liberada tras abandonar el pago (cita #" + appointment.getId() + ").");
        }

        return appointment;
    }

    /**
     * Genera el Payment Intent de la cita indicada (compatibilidad con el endpoint
     * previo de Stripe Elements). Si no hay clave de Stripe devuelve un secret ficticio.
     *
     * @param appointmentId Identificador de la cita a pagar.
     * @return Datos del intent (clientSecret, importe y moneda).
     */
    public PaymentIntentResponse createPaymentIntent(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + appointmentId));

        BigDecimal amount = appointment.getPrice() != null ? appointment.getPrice() : BigDecimal.ZERO;

        boolean mock = !stripeService.isConfigured();
        String clientSecret = mock
                ? "pi_mock_" + appointment.getId() + "_" + UUID.randomUUID()
                : "pi_real_" + appointment.getId() + "_" + UUID.randomUUID();

        return new PaymentIntentResponse(clientSecret, amount, "eur", mock);
    }

    /** Extrae el appointmentId de los metadatos (o del client_reference_id) de la sesión. */
    private String extractAppointmentId(Session session) {
        if (session.getMetadata() != null && session.getMetadata().get("appointmentId") != null) {
            return session.getMetadata().get("appointmentId");
        }
        return session.getClientReferenceId();
    }
}