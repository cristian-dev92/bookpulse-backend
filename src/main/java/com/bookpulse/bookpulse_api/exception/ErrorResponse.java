package com.bookpulse.bookpulse_api.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/**
 * Clase que define la estructura estándar de las respuestas de error
 * devueltas por la API de BookPulse hacia el cliente.
 * * @author Cristian
 * @since 1.0
 */
@Getter
@Setter
@AllArgsConstructor
public class ErrorResponse {

    /** Fecha y hora exacta en la que ocurrió el error. */
    private LocalDateTime timestamp;

    /** Código de estado HTTP (ej: 400, 404, 409). */
    private int status;

    /** El nombre del error HTTP (ej: Bad Request, Conflict). */
    private String error;

    /** Mensaje detallado y amigable que explica la causa del error. */
    private String message;

    /** La URI o ruta del endpoint donde se originó el fallo. */
    private String path;
}
