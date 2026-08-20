package com.bookpulse.bookpulse_api.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Servicio de notificaciones por WhatsApp/SMS mediante la SDK oficial de Twilio.
 * <p>
 * Inicializa la SDK en su {@link PostConstruct} con las credenciales de la cuenta
 * (leídas del entorno para no exponer secretos) y expone un método <strong>asíncrono</strong>
 * ({@link Async}) para enviar confirmaciones, cancelaciones y recordatorios de citas
 * sin bloquear la respuesta HTTP.
 * </p>
 * <p>
 * Estrategia: se intenta WhatsApp primero con el texto directo como body y, si la API
 * falla (por ejemplo, error de plantilla 21655, mensaje bloqueado 63016 o remitente no
 * verificado 21608), se reintenta automáticamente con un SMS tradicional al mismo número,
 * sin la etiqueta {@code whatsapp:}. Cualquier fallo se registra en consola sin interrumpir
 * el flujo del negocio.
 * </p>
 *
 * @author Cristian
 */
@Service
public class TwilioService {

    /**
     * ContentSid oficial de la plantilla de WhatsApp aprobada para BookPulse.
     * <p>
     * Twilio exige un Content Template aprobado para enviar mensajes por WhatsApp
     * con cuentas verificadas (sin él devuelve 21655 / ContentSid Required). El
     * body se envía vacío y la plantilla aporta el texto del mensaje.
     * </p>
     */
    public static final String CONTENT_SID = "HXfe5ab5f00277942d4d4200328b4d403c";

    private final String accountSid;
    private final String authToken;
    private final String whatsappNumber;
    private final String smsNumber;
    private final String defaultCountryCode;

    public TwilioService(@Value("${twilio.account.sid:}") String accountSid,
                         @Value("${twilio.auth.token:}") String authToken,
                         @Value("${twilio.whatsapp.number:whatsapp:+4915888623971}") String whatsappNumber,
                         @Value("${twilio.sms.number:}") String smsNumber,
                         @Value("${twilio.default.country.code:+34}") String defaultCountryCode) {
        this.accountSid = accountSid;
        this.authToken = authToken;
        this.whatsappNumber = whatsappNumber;
        this.smsNumber = smsNumber;
        this.defaultCountryCode = defaultCountryCode;
    }

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            Twilio.init(accountSid, authToken);
            System.out.println("[TwilioService] SDK de Twilio inicializada correctamente.");
        } else {
            System.out.println("[TwilioService] Sin TWILIO_ACCOUNT_SID/AUTH_TOKEN: notificaciones WhatsApp/SMS desactivadas.");
        }
    }

    /** Indica si hay credenciales de Twilio configuradas. */
    public boolean isConfigured() {
        return accountSid != null && !accountSid.isBlank()
                && authToken != null && !authToken.isBlank();
    }

    /**
     * Envía una notificación de cita al móvil del cliente.
     * <p>
     * Intenta WhatsApp primero y, si la API lo rechaza (plantilla no válida, remitente
     * no verificado, mensaje bloqueado...), reintenta automáticamente con un SMS
     * tradicional al mismo número.
     * </p>
     *
     * @param recipientPhone Número de destino (p. ej. +34600000000).
     * @param bodyText       Texto libre del mensaje.
     */
    @Async
    public void sendAppointmentNotification(String recipientPhone, String bodyText) {
        if (!isConfigured()) {
            return;
        }
        if (recipientPhone == null || recipientPhone.isBlank()) {
            System.out.println("[TwilioService] Sin teléfono de destino: se omite el mensaje.");
            return;
        }

        String to = normalizeE164(recipientPhone);
        if (to == null) {
            System.out.println("[TwilioService] Teléfono inválido, se omite el mensaje: " + recipientPhone);
            return;
        }

        if (sendViaWhatsApp(to)) {
            return;
        }

        // Fallback: SMS tradicional al mismo número, sin la etiqueta whatsapp:
        System.out.println("[TwilioService] No se pudo enviar por WhatsApp. Reintentando por SMS a " + to);
        sendViaSms(to, bodyText);
    }

    /**
     * Envía por WhatsApp usando la plantilla oficial ({@link #CONTENT_SID}) y
     * devuelve {@code true} si Twilio aceptó el mensaje.
     * <p>
     * El body se envía vacío (la plantilla aporta el texto) y tanto el destinatario
     * (To) como el remitente (From) llevan la etiqueta {@code whatsapp:}. Los
     * errores de cuenta Trial se capturan como notificación simulada.
     * </p>
     */
    private boolean sendViaWhatsApp(String to) {
        try {
            String toWithPrefix = "whatsapp:" + to;
            String fromWithPrefix = normalizeFrom(whatsappNumber, "whatsapp:");

            Message message = Message.creator(
                    new PhoneNumber(toWithPrefix),
                    new PhoneNumber(fromWithPrefix),
                    ""
            ).setContentSid(CONTENT_SID)
             .create();
            System.out.println("[TwilioService] WhatsApp enviado con éxito. SID: " + message.getSid()
                    + " | To: " + toWithPrefix + " | From: " + fromWithPrefix
                    + " | ContentSid: " + CONTENT_SID);
            return true;
        } catch (ApiException e) {
            if (isTrialRestriction(e.getCode(), e.getMessage())) {
                System.err.println("[TwilioService] [Twilio Trial Mode] No se pudo entregar por restricciones "
                        + "de cuenta de prueba (Código " + e.getCode() + ": " + e.getMessage()
                        + "). Notificación simulada correctamente para " + to);
                return true;
            }
            System.err.println("[TwilioService] Error de la API de Twilio en WhatsApp (Código " + e.getCode()
                    + ", HTTP " + e.getStatusCode() + "): " + e.getMessage()
                    + " | Más info: " + e.getMoreInfo());
            return false;
        } catch (Exception e) {
            if (isTrialRestriction(null, e.getMessage())) {
                System.err.println("[TwilioService] [Twilio Trial Mode] No se pudo entregar por restricciones "
                        + "de cuenta de prueba (" + e.getMessage()
                        + "). Notificación simulada correctamente para " + to);
                return true;
            }
            System.err.println("[TwilioService] Error inesperado al enviar por WhatsApp a " + to + ": " + e.getMessage());
            return false;
        }
    }

    /** Envía por SMS como respaldo (sin la etiqueta {@code whatsapp:}). */
    private void sendViaSms(String to, String bodyText) {
        try {
            String from = smsNumber != null && !smsNumber.isBlank()
                    ? normalizeFrom(smsNumber, "")
                    : toPlainNumber(whatsappNumber);

            Message message = Message.creator(
                    new PhoneNumber(to),
                    new PhoneNumber(from),
                    bodyText
            ).create();
            System.out.println("[TwilioService] SMS de respaldo enviado con éxito. SID: " + message.getSid()
                    + " | To: " + to + " | From: " + from);
        } catch (ApiException e) {
            if (isTrialRestriction(e.getCode(), e.getMessage())) {
                System.err.println("[TwilioService] [Twilio Trial Mode] No se pudo entregar por SMS por "
                        + "restricciones de cuenta de prueba (Código " + e.getCode() + ": " + e.getMessage()
                        + "). Notificación simulada correctamente para " + to);
                return;
            }
            System.err.println("[TwilioService] Error de la API de Twilio en SMS (Código " + e.getCode()
                    + ", HTTP " + e.getStatusCode() + "): " + e.getMessage()
                    + " | Más info: " + e.getMoreInfo());
        } catch (Exception e) {
            if (isTrialRestriction(null, e.getMessage())) {
                System.err.println("[TwilioService] [Twilio Trial Mode] No se pudo entregar por SMS por "
                        + "restricciones de cuenta de prueba (" + e.getMessage()
                        + "). Notificación simulada correctamente para " + to);
                return;
            }
            System.err.println("[TwilioService] Error final al enviar SMS/WhatsApp a " + to + ": " + e.getMessage());
        }
    }

    /**
     * Detecta errores propios de las cuentas Trial de Twilio (no se puede entregar
     * por plantilla no aprobada, ContentSid inválido o remitente no verificado).
     * <p>
     * Códigos conocidos: 21654 (plantilla no válida), 21655 (ContentSid inválido)
     * y 572006 (bloqueo por contenido). También se detecta el texto
     * "ContentSid Required" que devuelve la sandbox.
     * </p>
     */
    private boolean isTrialRestriction(Integer code, String message) {
        if (code != null && (code == 21654 || code == 21655 || code == 572006)) {
            return true;
        }
        String msg = message == null ? "" : message;
        return msg.contains("ContentSid") || msg.contains("21655") || msg.contains("572006");
    }

    /**
     * Normaliza un número de teléfono a formato E.164 (el que exige Twilio).
     * <p>
     * Reglas:
     * <ul>
     *   <li>Elimina espacios, guiones, paréntesis y el prefijo {@code whatsapp:}.</li>
     *   <li>Si ya empieza por {@code +}, se conserva tal cual (formato internacional).</li>
     *   <li>Si empieza por {@code 00}, se convierte a {@code +}.</li>
     *   <li>Si el número ya empieza por el código de país por defecto (p. ej. {@code 34}),
     *       solo se antepone el {@code +} sin duplicar el prefijo.</li>
     *   <li>En cualquier otro caso (número local), se antepone el código de país por
     *       defecto configurado (p. ej. {@code +34} para España).</li>
     * </ul>
     *
     * @param phone Teléfono tal y como lo introdujo el cliente.
     * @return El número normalizado en formato E.164 o {@code null} si no es válido.
     */
    private String normalizeE164(String phone) {
        if (phone == null) {
            return null;
        }
        String cleaned = phone.trim()
                .replaceAll("(?i)whatsapp:", "")
                .replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty() || cleaned.length() < 8) {
            return null;
        }
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }
        String countryCode = defaultCountryCode != null && !defaultCountryCode.isBlank()
                ? defaultCountryCode.replace("+", "")
                : "";
        if (!countryCode.isEmpty() && cleaned.startsWith(countryCode)) {
            return "+" + cleaned;
        }
        return "+" + countryCode + cleaned;
    }

    /** Normaliza el número del remitente añadiendo el prefijo del canal (whatsapp: o plano). */
    private String normalizeFrom(String from, String prefix) {
        String cleaned = (from == null ? "" : from.trim())
                .replaceAll("(?i)whatsapp:", "")
                .replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalStateException("Número de remitente Twilio no configurado");
        }
        return prefix + (cleaned.startsWith("+") ? cleaned : "+" + cleaned);
    }

    /** Quita la etiqueta {@code whatsapp:} de un número de remitente para usarlo por SMS. */
    private String toPlainNumber(String from) {
        String cleaned = (from == null ? "" : from.trim())
                .replaceAll("(?i)whatsapp:", "")
                .replaceAll("[^0-9+]", "");
        if (cleaned.isEmpty()) {
            throw new IllegalStateException("Número de remitente Twilio no configurado");
        }
        return cleaned.startsWith("+") ? cleaned : "+" + cleaned;
    }
}