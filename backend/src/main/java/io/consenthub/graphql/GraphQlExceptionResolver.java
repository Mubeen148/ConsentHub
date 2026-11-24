package io.consenthub.graphql;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.consenthub.domain.exception.BadRequestException;
import io.consenthub.domain.exception.ConflictException;
import io.consenthub.domain.exception.ConsentHubException;
import io.consenthub.domain.exception.ForbiddenException;
import io.consenthub.domain.exception.NotFoundException;
import io.consenthub.domain.exception.RateLimitExceededException;
import io.consenthub.domain.exception.UnauthorizedException;
import io.consenthub.sdui.SduiValidationException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;

/**
 * Uniform GraphQL exception resolver mapping domain, validation, and Spring Security
 * exceptions to standard GraphQL error classifications ({@code BAD_USER_INPUT},
 * {@code UNAUTHENTICATED}, {@code FORBIDDEN}, {@code NOT_FOUND}) without leaking
 * stack traces or internal database details ({@code SEC-G06}, {@code SEC-G10}).
 */
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    private static final Logger log = LoggerFactory.getLogger(GraphQlExceptionResolver.class);

    public enum GraphQlErrorClassification implements ErrorClassification {
        BAD_USER_INPUT,
        UNAUTHENTICATED,
        FORBIDDEN,
        NOT_FOUND,
        TOO_MANY_REQUESTS,
        INTERNAL_ERROR
    }

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof UnauthorizedException uex) {
            return buildError(env, GraphQlErrorClassification.UNAUTHENTICATED, uex.getCode(), uex.getMessage(), null);
        }
        if (ex instanceof AuthenticationException) {
            return buildError(
                    env,
                    GraphQlErrorClassification.UNAUTHENTICATED,
                    "UNAUTHENTICATED",
                    "Authentication is required to access this operation.",
                    null
            );
        }
        if (ex instanceof ForbiddenException fex) {
            return buildError(env, GraphQlErrorClassification.FORBIDDEN, fex.getCode(), fex.getMessage(), null);
        }
        if (ex instanceof AccessDeniedException) {
            return buildError(
                    env,
                    GraphQlErrorClassification.FORBIDDEN,
                    "FORBIDDEN",
                    "You do not have permission to perform this operation.",
                    null
            );
        }
        if (ex instanceof NotFoundException nfex) {
            return buildError(env, GraphQlErrorClassification.NOT_FOUND, nfex.getCode(), nfex.getMessage(), null);
        }
        if (ex instanceof EntityNotFoundException || ex instanceof NoSuchElementException) {
            return buildError(
                    env,
                    GraphQlErrorClassification.NOT_FOUND,
                    "NOT_FOUND",
                    "The requested resource could not be found.",
                    null
            );
        }
        if (ex instanceof RateLimitExceededException rlex) {
            return buildError(
                    env,
                    GraphQlErrorClassification.TOO_MANY_REQUESTS,
                    rlex.getCode(),
                    rlex.getMessage(),
                    null
            );
        }
        if (ex instanceof BadRequestException brex) {
            return buildError(
                    env,
                    GraphQlErrorClassification.BAD_USER_INPUT,
                    brex.getCode(),
                    brex.getMessage(),
                    brex.getFieldErrors()
            );
        }
        if (ex instanceof ConflictException cex) {
            return buildError(
                    env,
                    GraphQlErrorClassification.BAD_USER_INPUT,
                    cex.getCode(),
                    cex.getMessage(),
                    cex.getFieldErrors()
            );
        }
        if (ex instanceof ConsentHubException chex) {
            GraphQlErrorClassification classification = mapHttpStatus(chex.getStatus());
            return buildError(env, classification, chex.getCode(), chex.getMessage(), chex.getFieldErrors());
        }
        if (ex instanceof ConstraintViolationException cvex) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            for (ConstraintViolation<?> violation : cvex.getConstraintViolations()) {
                String path = violation.getPropertyPath() != null ? violation.getPropertyPath().toString() : "input";
                fieldErrors.put(path, violation.getMessage());
            }
            String message = fieldErrors.isEmpty()
                    ? "One or more input arguments failed validation."
                    : fieldErrors.values().iterator().next();
            return buildError(
                    env,
                    GraphQlErrorClassification.BAD_USER_INPUT,
                    "BAD_USER_INPUT",
                    message,
                    fieldErrors
            );
        }
        if (ex instanceof BindException bex) {
            Map<String, String> fieldErrors = new LinkedHashMap<>();
            for (FieldError fieldError : bex.getBindingResult().getFieldErrors()) {
                fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
            }
            return buildError(
                    env,
                    GraphQlErrorClassification.BAD_USER_INPUT,
                    "BAD_USER_INPUT",
                    "Invalid GraphQL input payload.",
                    fieldErrors
            );
        }
        if (ex instanceof IllegalArgumentException iaex) {
            String message = (iaex.getMessage() != null && !iaex.getMessage().isBlank())
                    ? iaex.getMessage()
                    : "Invalid request argument.";
            return buildError(env, GraphQlErrorClassification.BAD_USER_INPUT, "BAD_USER_INPUT", message, null);
        }
        if (ex instanceof SduiValidationException svex) {
            log.error("SDUI schema validation failed on field '{}': {}",
                    env.getField().getName(), svex.getViolations());
            return buildError(
                    env,
                    GraphQlErrorClassification.INTERNAL_ERROR,
                    "SDUI_SCHEMA_VIOLATION",
                    "Server-Driven UI screen failed schema safety validation.",
                    null
            );
        }

        log.error("Unhandled exception in GraphQL resolver '{}'", env.getField().getName(), ex);
        return buildError(
                env,
                GraphQlErrorClassification.INTERNAL_ERROR,
                "INTERNAL_ERROR",
                "An unexpected error occurred while processing the GraphQL operation.",
                null
        );
    }

    private static GraphQlErrorClassification mapHttpStatus(HttpStatus status) {
        if (status == null) {
            return GraphQlErrorClassification.INTERNAL_ERROR;
        }
        return switch (status) {
            case UNAUTHORIZED -> GraphQlErrorClassification.UNAUTHENTICATED;
            case FORBIDDEN -> GraphQlErrorClassification.FORBIDDEN;
            case NOT_FOUND -> GraphQlErrorClassification.NOT_FOUND;
            case TOO_MANY_REQUESTS -> GraphQlErrorClassification.TOO_MANY_REQUESTS;
            case BAD_REQUEST, CONFLICT, UNPROCESSABLE_ENTITY -> GraphQlErrorClassification.BAD_USER_INPUT;
            default -> GraphQlErrorClassification.INTERNAL_ERROR;
        };
    }

    private static GraphQLError buildError(
            DataFetchingEnvironment env,
            GraphQlErrorClassification classification,
            String code,
            String message,
            Map<String, String> fieldErrors
    ) {
        Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("classification", classification.name());
        extensions.put("code", code != null ? code : classification.name());
        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            extensions.put("correlationId", correlationId);
        }
        if (fieldErrors != null && !fieldErrors.isEmpty()) {
            extensions.put("fieldErrors", fieldErrors);
        }

        return GraphqlErrorBuilder.newError(env)
                .errorType(classification)
                .message(message)
                .extensions(extensions)
                .build();
    }
}
