package io.consenthub.web;

import io.consenthub.domain.dto.ApiErrorResponse;
import io.consenthub.domain.dto.TimestampFormatter;
import io.consenthub.domain.exception.ConsentHubException;
import io.consenthub.domain.exception.RateLimitExceededException;
import io.consenthub.sdui.SduiValidationException;
import io.consenthub.security.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized REST exception translator returning standardized {@link ApiErrorResponse} envelopes
 * without leaking internal stack traces or database details (SEC-G10).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ConsentHubException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(
            ConsentHubException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = ex.getStatus();
        HttpHeaders headers = new HttpHeaders();
        if (ex instanceof RateLimitExceededException rateEx) {
            headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(rateEx.getRetryAfterSeconds()));
        }
        return ResponseEntity.status(status)
                .headers(headers)
                .body(buildError(status, ex.getCode(), ex.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "One or more request fields failed validation.",
                        request,
                        fieldErrors
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "VALIDATION_ERROR",
                        "Missing required query parameter: " + ex.getParameterName(),
                        request,
                        Map.of(ex.getParameterName(), "Parameter is required")
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(
                        HttpStatus.BAD_REQUEST,
                        "MALFORMED_JSON",
                        "Request body could not be parsed as valid JSON.",
                        request,
                        null
                ));
    }

    @ExceptionHandler(SduiValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleSduiValidation(
            SduiValidationException ex,
            HttpServletRequest request
    ) {
        log.error("Server-side SDUI schema validation error: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "SDUI_SCHEMA_VIOLATION",
                        ex.getMessage(),
                        request,
                        null
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(
                        HttpStatus.FORBIDDEN,
                        "FORBIDDEN",
                        "You do not have permission to perform this action.",
                        request,
                        null
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        log.error("Unhandled server error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "INTERNAL_ERROR",
                        "An unexpected server error occurred.",
                        request,
                        null
                ));
    }

    private static ApiErrorResponse buildError(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            Map<String, String> fieldErrors
    ) {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        return new ApiErrorResponse(
                TimestampFormatter.format(Instant.now()),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request != null ? request.getRequestURI() : "",
                correlationId,
                fieldErrors
        );
    }
}
