package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.PaymentIntentRequest;
import com.bookpulse.bookpulse_api.dto.PaymentIntentResponse;
import com.bookpulse.bookpulse_api.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la integración con la pasarela de pagos.
 *
 * @author Cristian
 */
@RestController
@RequestMapping("/api/v1/payments")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000"})
public class PaymentController {

    private final PaymentService paymentService;

    @Autowired
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Crea un Payment Intent para la cita indicada.
     *
     * @param request Cuerpo con el identificador de la cita.
     * @return Datos del intent de pago (clientSecret para Stripe Elements).
     */
    @PostMapping("/create-intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(@RequestBody PaymentIntentRequest request) {
        PaymentIntentResponse response = paymentService.createPaymentIntent(request.appointmentId());
        return ResponseEntity.ok(response);
    }
}