package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado del envío de correos electrónicos de la aplicación.
 * <p>
 * Actualmente notifica al cliente cuando se crea o confirma una cita.
 * Todas las operaciones se protegen con try/catch para que un fallo de email
 * (p. ej. SMTP no configurado en desarrollo) nunca rompa el flujo de reservas.
 * </p>
 *
 * @author Cristian
 */
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${MAIL_FROM:no-reply@bookpulse.com}")
    private String fromAddress;

    @Autowired
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía un correo de confirmación de cita al email del cliente.
     *
     * @param appointment La cita creada o confirmada.
     */
    public void sendAppointmentConfirmation(Appointment appointment) {
        if (appointment == null
                || appointment.getUser() == null
                || appointment.getUser().getEmail() == null
                || appointment.getUser().getEmail().isBlank()) {
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(appointment.getUser().getEmail());
            message.setSubject("BookPulse - Confirmación de cita #" + appointment.getId());
            message.setText(buildAppointmentBody(appointment));

            mailSender.send(message);
            System.out.println("[EmailService] Correo enviado a " + appointment.getUser().getEmail());
        } catch (Exception e) {
            System.err.println("[EmailService] No se pudo enviar el correo: " + e.getMessage());
        }
    }

    private String buildAppointmentBody(Appointment appointment) {
        String clientName = appointment.getUser().getName() != null
                ? appointment.getUser().getName()
                : appointment.getUser().getEmail();
        String serviceName = appointment.getService() != null
                ? appointment.getService().getName()
                : "Servicio no especificado";

        return "Hola " + clientName + ",\n\n"
                + "Tu cita ha sido registrada con éxito en BookPulse.\n\n"
                + "Detalle de la cita:\n"
                + "  - Servicio: " + serviceName + "\n"
                + "  - Fecha: " + appointment.getStartTime() + "\n"
                + "  - Fin estimado: " + appointment.getEndTime() + "\n"
                + "  - Precio: " + (appointment.getPrice() != null ? appointment.getPrice() + " €" : "A confirmar") + "\n"
                + "  - Estado: " + appointment.getStatus() + "\n\n"
                + "Gracias por confiar en BookPulse.\n";
    }
}