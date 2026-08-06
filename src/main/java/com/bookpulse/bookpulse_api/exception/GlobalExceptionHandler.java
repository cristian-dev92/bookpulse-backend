package com.bookpulse.bookpulse_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.HashMap;
import java.util.Map;

import java.time.LocalDateTime;

/**
 * Controlador de asistencia global para la interceptación y tratamiento unificado
 * de excepciones en toda la API de BookPulse.
 * <p>
 * Centraliza los fallos del sistema convirtiéndolos en respuestas HTTP estructuradas
 * bajo la entidad {@link ErrorResponse}, garantizando que el Frontend reciba siempre
 * un formato predecible.
 * </p>
 *
 * @author Cristian
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Intercepta los fallos de concurrencia causados por el Bloqueo Optimista.
     * <p>
     * Se dispara cuando dos usuarios intentan reservar exactamente el mismo slot
     * horari al mismo tiempo y sus versiones de registro entran en conflicto.
     * </p>
     *
     * @param ex      La excepción de fallo de bloqueo capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 409 Conflict y el JSON de error personalizado.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLockingFailure(
            ObjectOptimisticLockingFailureException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                "Lo sentimos, este hueco horario acaba de ser reservado por otro usuario. Por favor, elige otra hora.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Gestiona las excepciones de lógica de negocio o parámetros incorrectos.
     *
     * @param ex      La excepción de argumento ilegal capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Interceptor genérico para cualquier error inesperado del servidor (Errores 500).
     * Evita que se filtren datos internos del backend por seguridad.
     *
     * @param ex      Cualquier excepción no controlada explícitamente.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGlobalException(
            Exception ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                "Ha ocurrido un error interno en el servidor. Si el problema persiste, contacte con soporte.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Map<String, String> errorResponse = new HashMap<>();

        // Mensaje limpio para el usuario
        errorResponse.put("message", "El nombre de usuario o email ya se encuentra registrado.");

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // 409 Conflict
    }

}
