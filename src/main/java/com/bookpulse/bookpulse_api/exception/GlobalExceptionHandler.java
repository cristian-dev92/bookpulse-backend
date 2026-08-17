package com.bookpulse.bookpulse_api.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
     * Gestiona los cuerpos JSON ilegibles (fechas con formato inválido, JSON malformado).
     * Evita que un fallo de deserialización se convierta en un 500 genérico.
     *
     * @param ex      La excepción de deserialización capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 400 Bad Request.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Formato de fecha u hora inválido. Asegúrate de enviar startTime como yyyy-MM-dd'T'HH:mm:ss.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestiona los fallos de validación {@code @Valid} de los DTOs.
     * Devuelve 400 con el detalle de cada campo que no cumplió las restricciones.
     *
     * @param ex      La excepción de validación capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestiona los intentos de acceso a citas o recursos ajenos al usuario logueado.
     *
     * @param ex      La excepción de acceso denegado capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.FORBIDDEN.value(),
                HttpStatus.FORBIDDEN.getReasonPhrase(),
                "No tienes permisos para realizar esta operación.",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.FORBIDDEN);
    }

    /**
     * Gestiona las excepciones de estado de negocio (p. ej. un servicio que no se puede
     * eliminar por tener citas asociadas). Devuelve 409 Conflict con el mensaje claro.
     *
     * @param ex      La excepción de estado ilegal capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 409 Conflict.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                HttpStatus.CONFLICT.getReasonPhrase(),
                ex.getMessage(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.CONFLICT);
    }

    /**
     * Gestiona los fallos de conversión de parámetros (p. ej. un estado inválido
     * como {@code status=FOO} o una página no numérica). Devuelve 400 en lugar de un 500.
     *
     * @param ex      La excepción de tipo de argumento capturada.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con estado 400 Bad Request.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, WebRequest request) {

        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue(),
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    /**
     * Gestiona las excepciones de negocio lanzadas con {@link org.springframework.web.server.ResponseStatusException}.
     * <p>
     * Sin este handler, el catch-all genérico ({@code @ExceptionHandler(Exception.class)}) interceptaría
     * la excepción y devolvería un 500 genérico en lugar del código HTTP y mensaje previstos.
     * </p>
     *
     * @param ex      La excepción con el estado HTTP deseado.
     * @param request El contexto de la petición web actual.
     * @return Un {@link ResponseEntity} con el estado HTTP y el mensaje indicados.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(
            ResponseStatusException ex, WebRequest request) {

        int status = ex.getStatusCode().value();
        ErrorResponse error = new ErrorResponse(
                LocalDateTime.now(),
                status,
                ex.getStatusCode().toString(),
                ex.getReason() != null ? ex.getReason() : "Solicitud incorrecta",
                request.getDescription(false).replace("uri=", "")
        );

        return new ResponseEntity<>(error, ex.getStatusCode());
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

        // Registramos la causa raíz real en el log del servidor
        System.err.println("[DataIntegrityViolation] " + (ex.getMostSpecificCause() != null
                ? ex.getMostSpecificCause().getMessage() : ex.getMessage()));

        String detail = (ex.getMostSpecificCause() != null)
                ? ex.getMostSpecificCause().getMessage()
                : ex.getMessage();

        // Si la restricción involucra la tabla de usuarios, seguimos dando el mensaje amigable de registro.
        // Para el resto de violaciones (p. ej. citas), mostramos la restricción real para facilitar el debug.
        boolean isUserUnique = detail != null && detail.toLowerCase().contains("users");
        errorResponse.put("message", isUserUnique
                ? "El nombre de usuario o email ya se encuentra registrado."
                : "La operación no se pudo completar por una restricción de datos: " + detail);

        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse); // 409 Conflict
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {

        ErrorResponse errorResponse = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.NOT_FOUND.value(),
                "Not Found",
                ex.getMessage(), // Aquí irá: "Este usuario no existe"
                request.getDescription(false)
        );
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentialsException(
            InvalidCredentialsException ex,
            WebRequest request) {

        ErrorResponse errorDetails = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNAUTHORIZED.value(),
                "Unauthorized",
                ex.getMessage(), // "Correo electrónico o contraseña incorrectos"
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExistsException(
            UserAlreadyExistsException ex,
            WebRequest request) {

        ErrorResponse errorDetails = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.CONFLICT.value(),
                "Conflict",
                ex.getMessage(), // "El correo electrónico ya está registrado"
                request.getDescription(false)
        );

        return new ResponseEntity<>(errorDetails, HttpStatus.CONFLICT);
    }

}
