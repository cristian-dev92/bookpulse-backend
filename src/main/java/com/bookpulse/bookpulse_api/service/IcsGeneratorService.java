package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.model.Appointment;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Servicio auxiliar que genera dinámicamente un archivo de calendario en formato
 * iCalendar ({@code .ics}) en memoria a partir de los datos de una cita.
 * <p>
 * El contenido resultante se adjunta a los correos transaccionales para que el
 * cliente pueda añadir la reserva con un clic a Google/Apple Calendar.
 * </p>
 * <p>
 * Las horas se escriben como "hora flotante" (sin zona horaria), de modo que el
 * calendario del destinatario las interprete en su propia zona local, igual que
 * hace la web al mostrar las citas.
 * </p>
 *
 * @author Cristian
 */
@Service
public class IcsGeneratorService {

    private static final DateTimeFormatter ICS_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter ICS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** Nombre de la entidad/negocio que aparece en los eventos. */
    private static final String BUSINESS_NAME = "BookPulse";
    private static final String PROD_ID = "-//BookPulse//BookPulse Calendar//ES";
    private static final String LINE_SEP = "\r\n";

    /**
     * Genera el contenido {@code .ics} completo para una cita.
     *
     * @param appointment  La cita de la que se extraen los datos.
     * @param includeStatus {@code true} para marcar el estado del evento como
     *                     CONFIRMED/CANCELLED/TENTATIVE según el estado de la cita.
     * @return El contenido iCalendar con las líneas terminadas en CRLF.
     */
    public String generateIcs(Appointment appointment, boolean includeStatus) {
        if (appointment == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR").append(LINE_SEP);
        sb.append("VERSION:2.0").append(LINE_SEP);
        sb.append("PRODID:").append(PROD_ID).append(LINE_SEP);
        sb.append("CALSCALE:GREGORIAN").append(LINE_SEP);
        sb.append("METHOD:PUBLISH").append(LINE_SEP);
        sb.append("BEGIN:VEVENT").append(LINE_SEP);

        String uid = appointment.getId() != null
                ? "bookpulse-cita-" + appointment.getId() + "@bookpulse.com"
                : "bookpulse-cita-@bookpulse.com";

        sb.append("UID:").append(uid).append(LINE_SEP);
        sb.append("DTSTAMP:").append(format(ICS_DATE_TIME, LocalDateTime.now())).append(LINE_SEP);

        LocalDateTime start = appointment.getStartTime();
        LocalDateTime end = appointment.getEndTime() != null ? appointment.getEndTime() : start;

        sb.append("DTSTART:").append(format(ICS_DATE_TIME, start)).append(LINE_SEP);
        sb.append("DTEND:").append(format(ICS_DATE_TIME, end)).append(LINE_SEP);

        String serviceName = appointment.getService() != null
                ? appointment.getService().getName()
                : "Servicio";
        sb.append("SUMMARY:").append(escapeText(BUSINESS_NAME + " - " + serviceName)).append(LINE_SEP);

        String description = buildDescription(appointment);
        sb.append("DESCRIPTION:").append(escapeText(description)).append(LINE_SEP);
        sb.append("LOCATION:").append(escapeText(BUSINESS_NAME)).append(LINE_SEP);

        if (includeStatus) {
            sb.append("STATUS:").append(mapIcsStatus(appointment)).append(LINE_SEP);
        }

        sb.append("END:VEVENT").append(LINE_SEP);
        sb.append("END:VCALENDAR").append(LINE_SEP);

        return sb.toString();
    }

    /**
     * Devuelve el nombre de archivo sugerido para el adjunto {@code .ics}.
     *
     * @param appointment Cita a partir de la cual se nombra el archivo.
     * @return Un nombre de archivo válido, p. ej. {@code cita-42.ics}.
     */
    public String getIcsFileName(Appointment appointment) {
        Long id = appointment != null ? appointment.getId() : null;
        return "cita-" + (id != null ? id : "nueva") + ".ics";
    }

    private String buildDescription(Appointment appointment) {
        String serviceName = appointment.getService() != null
                ? appointment.getService().getName()
                : "Servicio no especificado";
        String price = appointment.getPrice() != null
                ? appointment.getPrice().setScale(2, RoundingMode.HALF_UP) + " EUR"
                : "A confirmar";
        String start = appointment.getStartTime() != null
                ? appointment.getStartTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a las' HH:mm", Locale.getDefault()))
                : "-";

        return "Reserva en " + BUSINESS_NAME + ". "
                + "Servicio: " + serviceName + ". "
                + "Fecha y hora: " + start + ". "
                + "Precio: " + price + ".";
    }

    private String mapIcsStatus(Appointment appointment) {
        if (appointment.getStatus() == null) {
            return "CONFIRMED";
        }
        return switch (appointment.getStatus()) {
            case CANCELLED -> "CANCELLED";
            case PENDING -> "TENTATIVE";
            default -> "CONFIRMED";
        };
    }

    private String format(DateTimeFormatter formatter, LocalDateTime value) {
        return value != null ? value.format(formatter) : "";
    }

    /**
     * Escapa los caracteres especiales del formato iCalendar
     * (coma, punto y coma y barra invertida) y elimina saltos de línea.
     */
    private String escapeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r", "")
                .replace("\n", "\\n");
    }

    /** Convierte el contenido a un arreglo de bytes UTF-8 (adjunto MIME). */
    public byte[] toBytes(String icsContent) {
        return icsContent != null ? icsContent.getBytes(StandardCharsets.UTF_8) : new byte[0];
    }
}
