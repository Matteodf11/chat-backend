package com.matteo.chat_backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // Error response record for consistent structure
    private record ErrorResponse(
            Instant timestamp,
            int status,
            String error,
            String message,
            String path,
            Map<String, String> details
    ) {}

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, "Recurso no encontrado", ex.getMessage(), request.getRequestURI(),
                Map.of("resourceType", ex.getResourceType(), "resourceId", ex.getResourceId()));
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Acceso denegado", ex.getMessage(), request.getRequestURI(),
                Map.of("action", ex.getAction(), "resourceType", ex.getResourceType(), "resourceId", ex.getResourceId()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), request.getRequestURI(),
                Map.of("resourceType", ex.getResourceType(), "field", ex.getField(), "value", ex.getValue()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "No autorizado", ex.getMessage(), request.getRequestURI(),
                Map.of("reason", ex.getReason()));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, "No autorizado", "Credenciales no válidas", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Error de validación", ex.getMessage(), request.getRequestURI(),
                ex.getFieldErrors() != null ? ex.getFieldErrors() : Map.of());
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones", ex.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para realizar esta acción", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Argumento inválido", ex.getMessage(), request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Archivo demasiado grande", "El archivo supera el tamaño máximo permitido", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(MultipartException ex, jakarta.servlet.http.HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, "Error de archivo", "No se pudo leer el archivo seleccionado", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err ->
                errors.put(err.getField(), err.getDefaultMessage())
        );
        return buildError(HttpStatus.BAD_REQUEST, "Error de validación", "Los datos enviados no son válidos", request.getRequestURI(), errors);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Unexpected error for {}", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ha ocurrido un error inesperado", request.getRequestURI(), Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, jakarta.servlet.http.HttpServletRequest request) {
        log.error("Unexpected error for {}", request.getRequestURI(), ex);
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", "Ha ocurrido un error inesperado", request.getRequestURI(), Map.of());
    }

    private ResponseEntity<ErrorResponse> buildError(HttpStatus status, String error, String message, String path, Map<String, String> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(
                Instant.now(),
                status.value(),
                error,
                message,
                path,
                details
        ));
    }
}
