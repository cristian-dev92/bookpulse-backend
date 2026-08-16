package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.PaymentIntentResponse;
import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Servicio de integración con la pasarela de pagos (Stripe Sandbox).
 * <p>
 * Estado actual: preparación. Sin STRIPE_SECRET_KEY definida en el entorno,
 * el endpoint devuelve un clientSecret ficticio para que el frontend pueda
 * conectar Stripe Elements. Cuando exista la clave y la SDK de Stripe, la
 * creación del {@code PaymentIntent} real se ubicará en este método.
 * </p>
 *
 * @author Cristian
 */
@Service
public class PaymentService {

    private final AppointmentRepository appointmentRepository;

    @Value("${STRIPE_SECRET_KEY:}")
    private String stripeSecretKey;

    public PaymentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Genera el Payment Intent de la cita indicada.
     *
     * @param appointmentId Identificador de la cita.
     * @return Datos del intent (clientSecret, importe y moneda).
     */
    public PaymentIntentResponse createPaymentIntent(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró la cita con ID: " + appointmentId));

        BigDecimal amount = appointment.getPrice() != null ? appointment.getPrice() : BigDecimal.ZERO;

        boolean mock = (stripeSecretKey == null || stripeSecretKey.isBlank());
        String clientSecret = mock
                ? "pi_mock_" + appointment.getId() + "_" + UUID.randomUUID()
                : "pi_real_" + appointment.getId() + "_" + UUID.randomUUID();

        // TODO Stripe SDK: cuando STRIPE_SECRET_KEY esté configurada, crear aquí el PaymentIntent real:
        //   Stripe.apiKey = stripeSecretKey;
        //   PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
        //       .setAmount(amount.movePointRight(2).longValueExact())
        //       .setCurrency("eur")
        //       .setAutomaticPaymentMethods(...)
        //       .build();
        //   clientSecret = PaymentIntent.create(params).getClientSecret();

        return new PaymentIntentResponse(clientSecret, amount, "eur", mock);
    }
}