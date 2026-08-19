package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AppointmentCreateDTO;
import com.bookpulse.bookpulse_api.dto.AppointmentResponseDTO;
import com.bookpulse.bookpulse_api.dto.CheckoutSessionResponse;
import com.bookpulse.bookpulse_api.dto.PaymentConfirmRequest;
import com.bookpulse.bookpulse_api.dto.PaymentIntentRequest;
import com.bookpulse.bookpulse_api.dto.PaymentIntentResponse;
import com.bookpulse.bookpulse_api.mapper.AppointmentMapper;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la integración con Stripe Checkout.
 * <p>
 * Expone los endpoints para iniciar una sesión de pago, confirmarla tras el
 * redireccionamiento de Stripe y recibir los eventos de webhook.
 * </p>
 *
 * @author Cristian
 */
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class PaymentController {

    private final PaymentService paymentService;
    private final AppointmentMapper appointmentMapper;

    @Autowired
    public PaymentController(PaymentService paymentService, AppointmentMapper appointmentMapper) {
        this.paymentService = paymentService;
        this.appointmentMapper = appointmentMapper;
    }

    /**
     * Inicia el flujo de pago: crea la cita en estado PENDING_PAYMENT y devuelve
     * la URL de Stripe Checkout a la que se redirige al usuario.
     *
     * @param dto         Datos de la cita (startTime, serviceId, notes).
     * @param currentUser Usuario autenticado que realiza la reserva.
     * @return URL de Checkout, ID de la sesión y si es modo simulado.
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<CheckoutSessionResponse> createCheckoutSession(
            @Valid @RequestBody AppointmentCreateDTO dto,
            @AuthenticationPrincipal User currentUser) {
        requireAuthenticated(currentUser);
        CheckoutSessionResponse response = paymentService.createCheckoutSession(dto, currentUser);
        return ResponseEntity.ok(response);
    }

    /**
     * Confirma el pago tras el redireccionamiento del usuario desde Stripe.
     * <p>
     * Recibe el {@code session_id} de la URL, verifica en Stripe que el cobro se
     * completó y pasa la cita a CONFIRMED + PAID, enviando el correo con el .ics.
     * </p>
     *
     * @param request Cuerpo con el {@code sessionId}.
     * @return La cita confirmada y pagada.
     */
    @PostMapping("/confirm")
    public ResponseEntity<AppointmentResponseDTO> confirmPayment(@RequestBody PaymentConfirmRequest request) {
        Appointment confirmed = paymentService.confirmPayment(request.sessionId());
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(confirmed));
    }

    /**
     * Webhook de Stripe: escucha el evento {@code checkout.session.completed}.
     * <p>
     * Debe ser accesible sin autenticación y devuelve 200 para que Stripe no
     * reintente el envío. La firma se valida con {@code stripe.webhook.secret}.
     * </p>
     *
     * @param payload   Cuerpo crudo de la petición.
     * @param sigHeader Cabecera {@code Stripe-Signature}.
     * @return 200 si el evento se procesó, 204 si no era relevante.
     */
    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        Appointment confirmed = paymentService.handleWebhookEvent(payload, sigHeader);
        if (confirmed != null) {
            return ResponseEntity.ok("ok");
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Marca como CANCELLED una cita que quedó en PENDING_PAYMENT tras abandonar
     * el pago en Stripe (invocado desde la vista /booking-cancel).
     *
     * @param request Cuerpo con el {@code sessionId} de la sesión abandonada.
     * @return La cita liberada (estado CANCELLED si seguía pendiente).
     */
    @PostMapping("/cancel-session")
    public ResponseEntity<AppointmentResponseDTO> cancelSession(@RequestBody PaymentConfirmRequest request) {
        Appointment cancelled = paymentService.cancelPendingSession(request.sessionId());
        return ResponseEntity.ok(appointmentMapper.toResponseDTO(cancelled));
    }

    /**
     * Crea un Payment Intent para la cita indicada (compatibilidad con Stripe Elements).
     *
     * @param request Cuerpo con el identificador de la cita.
     * @return Datos del intent de pago (clientSecret para Stripe Elements).
     */
    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(@RequestBody PaymentIntentRequest request) {
        PaymentIntentResponse response = paymentService.createPaymentIntent(request.appointmentId());
        return ResponseEntity.ok(response);
    }

    private void requireAuthenticated(User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Debes iniciar sesión para realizar esta operación.");
        }
    }
}