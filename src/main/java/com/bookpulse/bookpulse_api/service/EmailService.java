package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import com.bookpulse.bookpulse_api.model.AppointmentStatus;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.AppointmentRepository;
import jakarta.mail.util.ByteArrayDataSource;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio de envío de correos electrónicos transaccionales de BookPulse.
 * <p>
 * Todos los métodos son <strong>asíncronos</strong> ({@link Async}) para no
 * bloquear la respuesta HTTP de la API, y se ejecutan en su propia transacción
 * ({@code REQUIRES_NEW}) volviendo a cargar la cita con sus relaciones para
 * evitar {@code LazyInitializationException} al trabajar fuera del hilo original.
 * Cada envío se protege con try/catch: un fallo de SMTP nunca rompe el flujo
 * de reservas.
 * </p>
 *
 * @author Cristian
 */
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.getDefault());
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());

    private final JavaMailSender mailSender;
    private final AppointmentRepository appointmentRepository;
    private final IcsGeneratorService icsGeneratorService;

    @Value("${mail.from:onboarding@resend.dev}")
    private String fromAddress;

    @Value("${booking.admin.email:admin@demo.com}")
    private String adminEmail;

    /**
     * Sobrescritura opcional del destinatario del cliente, pensada SOLO para
     * desarrollo: si se define, todos los correos dirigidos al cliente se
     * redirigen a esta dirección (p. ej. el plan gratuito de Resend solo permite
     * enviar a remitentes verificados). Vacío por defecto => se usa el email real.
     */
    @Value("${booking.test.to:}")
    private String testRecipientOverride;

    @Autowired
    public EmailService(JavaMailSender mailSender,
                        AppointmentRepository appointmentRepository,
                        IcsGeneratorService icsGeneratorService) {
        this.mailSender = mailSender;
        this.appointmentRepository = appointmentRepository;
        this.icsGeneratorService = icsGeneratorService;
    }

    /**
     * Correo de confirmación de reserva al cliente, con resumen HTML y adjunto {@code .ics}.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendBookingConfirmation(Appointment appointment) {
        Appointment fresh = reload(appointment);
        if (fresh == null) {
            return;
        }

        String clientEmail = resolveClientEmail(fresh.getUser());
        if (clientEmail == null) {
            return;
        }

        try {
            String title = "Reserva confirmada en BookPulse";
            String summary = summaryTable(fresh);
            String inner = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu reserva ha sido registrada con éxito. Este es el detalle de tu cita:</p>
                %s
                <p style="margin-top:20px;">Te hemos adjuntado el evento en formato <strong>.ics</strong>: ábrelo desde el correo para añadir tu cita a Google Calendar o Apple Calendar con un solo clic.</p>
                """.formatted(displayName(fresh.getUser()), summary);

            sendMime(clientEmail, title, buildHtml(title, inner), fresh);
            logSent("confirmación de reserva", clientEmail);
        } catch (Exception e) {
            logError("No se pudo enviar el correo de confirmación", e);
        }
    }

    /**
     * Aviso al administrador cuando se recibe una nueva reserva, con los datos del cliente.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendAdminNotification(Appointment appointment) {
        Appointment fresh = reload(appointment);
        if (fresh == null) {
            return;
        }

        try {
            String clientName = displayName(fresh.getUser());
            String clientEmail = emailOf(fresh.getUser());
            String clientPhone = fresh.getUser() != null ? fresh.getUser().getPhone() : null;

            String clientInfo = """
                <table class="summary">
                  <tr><td>Cliente</td><td>%s</td></tr>
                  <tr><td>Email</td><td>%s</td></tr>
                  <tr><td>Teléfono</td><td>%s</td></tr>
                  <tr><td>Servicio</td><td>%s</td></tr>
                  <tr><td>Fecha y hora</td><td>%s</td></tr>
                  <tr><td>Precio</td><td>%s</td></tr>
                </table>
                """.formatted(
                    clientName != null ? clientName : "—",
                    clientEmail != null ? clientEmail : "—",
                    clientPhone != null && !clientPhone.isBlank() ? clientPhone : "—",
                    serviceName(fresh),
                    formatDateTime(fresh.getStartTime()),
                    formatPrice(fresh.getPrice())
            );

            String title = "Nueva reserva recibida - #" + fresh.getId();
            String inner = "<p>Se ha recibido una nueva reserva en BookPulse. Revisa el panel de administración para gestionarla:</p>"
                    + clientInfo;

            sendMime(adminEmail, title, buildHtml(title, inner), fresh);
            logSent("notificación al administrador", adminEmail);
        } catch (Exception e) {
            logError("No se pudo enviar la notificación al administrador", e);
        }
    }

    /**
     * Correo de cancelación al cliente, con aviso del reembolso si procede.
     *
     * @param appointment Cita cancelada.
     * @param refunded    {@code true} si el pago fue reembolsado automáticamente.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendCancellationNotice(Appointment appointment, boolean refunded) {
        Appointment fresh = reload(appointment);
        if (fresh == null) {
            return;
        }

        String clientEmail = resolveClientEmail(fresh.getUser());
        if (clientEmail == null) {
            return;
        }

        System.out.println("[EmailService] Enviando correo de cancelación a: " + clientEmail);

        try {
            String title = "Tu cita en BookPulse ha sido cancelada";
            String inner = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu cita para <strong>%s</strong> del <strong>%s</strong> ha sido <strong>cancelada</strong>.</p>
                %s
                <p>Si esto ha sido un error o deseas reservar otro hueco, puedes hacerlo desde tu panel de usuario.</p>
                """.formatted(
                    displayName(fresh.getUser()),
                    serviceName(fresh),
                    formatDateTime(fresh.getStartTime()),
                    refunded
                            ? "<p style=\"color:#059669;\">El importe de tu reserva ha sido <strong>reembolsado automáticamente</strong> a tu método de pago. Puede tardar unos días en aparecer en tu cuenta.</p>"
                            : "<p>No se ha realizado ningún cargo o el reembolso no aplicaba a esta reserva.</p>"
            );

            sendMime(clientEmail, title, buildHtml(title, inner), fresh);
            logSent("notificación de cancelación", clientEmail);
        } catch (Exception e) {
            logError("No se pudo enviar la notificación de cancelación", e);
        }
    }

    /**
     * Correo de reprogramación al cliente con la nueva fecha/hora y el nuevo {@code .ics}.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sendRescheduleNotice(Appointment appointment) {
        Appointment fresh = reload(appointment);
        if (fresh == null) {
            return;
        }

        String clientEmail = resolveClientEmail(fresh.getUser());
        if (clientEmail == null) {
            return;
        }

        try {
            String title = "Tu cita en BookPulse ha sido reprogramada";
            String summary = summaryTable(fresh);
            String inner = """
                <p>Hola <strong>%s</strong>,</p>
                <p>Tu cita ha sido <strong>reprogramada</strong>. Consulta la nueva fecha y hora:</p>
                %s
                <p style="margin-top:20px;">El adjunto <strong>.ics</strong> se ha actualizado con la nueva fecha: vuelve a abrirlo para mantener tu calendario sincronizado.</p>
                """.formatted(displayName(fresh.getUser()), summary);

            sendMime(clientEmail, title, buildHtml(title, inner), fresh);
            logSent("notificación de reprogramación", clientEmail);
        } catch (Exception e) {
            logError("No se pudo enviar la notificación de reprogramación", e);
        }
    }

    // ------------------------------------------------------------------
    //  Utilidades internas
    // ------------------------------------------------------------------

    /**
     * Recarga la cita con sus relaciones (usuario y servicio) dentro de la
     * transacción del hilo asíncrono para evitar accesos perezosos fuera de sesión.
     */
    private Appointment reload(Appointment appointment) {
        if (appointment == null || appointment.getId() == null) {
            return appointment;
        }
        return appointmentRepository.findByIdWithRelations(appointment.getId()).orElse(null);
    }

    private void sendMime(String to, String subject, String htmlBody, Appointment appointment) throws jakarta.mail.MessagingException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

        helper.setFrom(fromAddress);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);

        String icsContent = icsGeneratorService.generateIcs(appointment, true);
        if (!icsContent.isBlank()) {
            helper.addAttachment(
                    icsGeneratorService.getIcsFileName(appointment),
                    new ByteArrayDataSource(icsGeneratorService.toBytes(icsContent), "text/calendar; charset=UTF-8")
            );
        }

        mailSender.send(mimeMessage);
    }

    /** Plantilla HTML con estilos inline ligeros y marca BookPulse. */
    private String buildHtml(String title, String contentHtml) {
        return """
            <!DOCTYPE html>
            <html lang="es">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width, initial-scale=1.0">
              <title>%s</title>
              <style>
                body { font-family: 'Segoe UI', Arial, sans-serif; background-color: #f1f5f9; margin: 0; padding: 24px; }
                .card { max-width: 560px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; border: 1px solid #e2e8f0; }
                .header { background: linear-gradient(90deg, #1e293b, #334155); color: #ffffff; padding: 20px 24px; }
                .header h1 { margin: 0; font-size: 19px; }
                .content { padding: 24px; color: #0f172a; font-size: 14px; line-height: 1.6; }
                table.summary { width: 100%%; border-collapse: collapse; margin-top: 8px; }
                table.summary td { padding: 8px 12px; border-bottom: 1px solid #f1f5f9; font-size: 13px; }
                table.summary td:first-child { color: #64748b; font-weight: 600; width: 40%%; }
                .footer { padding: 16px 24px; background: #f8fafc; color: #94a3b8; font-size: 12px; text-align: center; border-top: 1px solid #e2e8f0; }
              </style>
            </head>
            <body>
              <div class="card">
                <div class="header"><h1>%s</h1></div>
                <div class="content">%s</div>
                <div class="footer">BookPulse &middot; Gestión de citas online</div>
              </div>
            </body>
            </html>
            """.formatted(title, title, contentHtml);
    }

    private String summaryTable(Appointment appointment) {
        String status = appointment.getStatus() != null ? appointment.getStatus().name() : "PENDING";
        return """
            <table class="summary">
              <tr><td>Servicio</td><td>%s</td></tr>
              <tr><td>Fecha</td><td>%s</td></tr>
              <tr><td>Hora de inicio</td><td>%s</td></tr>
              <tr><td>Hora de fin</td><td>%s</td></tr>
              <tr><td>Precio</td><td>%s</td></tr>
              <tr><td>Estado</td><td>%s</td></tr>
            </table>
            """.formatted(
                serviceName(appointment),
                appointment.getStartTime() != null ? appointment.getStartTime().format(DATE_FORMATTER) : "—",
                formatTime(appointment.getStartTime()),
                formatTime(appointment.getEndTime()),
                formatPrice(appointment.getPrice()),
                status
        );
    }

    private String serviceName(Appointment appointment) {
        return appointment.getService() != null && appointment.getService().getName() != null
                ? appointment.getService().getName()
                : "Servicio no especificado";
    }

    private String displayName(User user) {
        return user != null && user.getName() != null && !user.getName().isBlank()
                ? user.getName()
                : (user != null && user.getEmail() != null ? user.getEmail() : "cliente");
    }

    private String emailOf(User user) {
        return user != null && user.getEmail() != null && !user.getEmail().isBlank()
                ? user.getEmail()
                : null;
    }

    /**
     * Resuelve el destinatario final de los correos dirigidos al cliente.
     * En desarrollo, si {@code booking.test.to} está definido (p. ej. una cuenta
     * verificada en Resend), se usa esa dirección en lugar del email real del
     * cliente para poder recibir las pruebas.
     */
    private String resolveClientEmail(User user) {
        String realEmail = emailOf(user);
        if (testRecipientOverride != null && !testRecipientOverride.isBlank()) {
            System.out.println("[EmailService] Override de prueba activo: enviando a " + testRecipientOverride
                    + " (email real del cliente: " + realEmail + ")");
            return testRecipientOverride;
        }
        return realEmail;
    }

    private String formatDateTime(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FORMATTER) : "—";
    }

    private String formatTime(LocalDateTime value) {
        return value != null ? value.format(DateTimeFormatter.ofPattern("HH:mm")) : "—";
    }

    private String formatPrice(BigDecimal price) {
        return price != null ? price.setScale(2, RoundingMode.HALF_UP) + " €" : "A confirmar";
    }

    private void logSent(String type, String to) {
        System.out.println("[EmailService] " + type + " enviado a " + to);
    }

    private void logError(String message, Exception e) {
        System.err.println("[EmailService] " + message + ": " + e.getMessage());
    }
}
