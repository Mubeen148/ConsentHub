package io.consenthub.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.consenthub.domain.dto.ApiErrorResponse;
import io.consenthub.domain.dto.TimestampFormatter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/**
 * Writes uniform JSON error envelopes from servlet filters and Spring Security handlers.
 */
@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        ApiErrorResponse payload = new ApiErrorResponse(
                TimestampFormatter.format(Instant.now()),
                status.value(),
                status.name(),
                code,
                message,
                request.getRequestURI(),
                correlationId,
                null
        );
        objectMapper.writeValue(response.getWriter(), payload);
    }
}
