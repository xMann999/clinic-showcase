package com.example.clinic.error;

import com.example.clinic.logging.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Translates MVC and application exceptions into clinic HTTP errors.
 *
 * <h2>Responsibility</h2>
 * <p>Handles domain failures, input errors, optimistic conflicts and recognized database constraints;
 * uses the framework base class for MVC exception dispatch.
 *
 * <h2>How it works</h2>
 * <p>Builds ApiError with the injected clock, request path, request ID and ordered field violations.
 * ResponseEntity selects status, headers and body; it is not an extra serialized JSON wrapper.
 *
 * <h2>Design choices</h2>
 * <p>Domain messages are exposed deliberately; unexpected diagnostics are logged with a stack trace
 * and replaced by generic client text. Rejected input values are omitted.
 *
 * <h2>Important details</h2>
 * <p>Framework statuses and headers are preserved by overrides. A 406 response is intentionally bodyless.
 * Servlet filter/container failures occur outside this advice and may use a different error path.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private final Clock clock;

    /**
     * Translates a domain category into a status and exposes client-safe domain text.
     *
     * <p>Application validation exceptions also supply field violations. Logs the rejection code/status
     * without a stack trace; callers are responsible for keeping domain messages safe.
     *
     * @param exception the failure being translated
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @ExceptionHandler(DomainException.class)
    ResponseEntity<ApiError> handleDomain(DomainException exception, HttpServletRequest request) {
        HttpStatus status = statusFor(exception.code().category());
        List<FieldViolation> violations = exception instanceof RequestValidationException validation
            ? validation.violations()
            : List.of();

        log.info("Request rejected: code={}, status={}", exception.code(), status.value());
        return build(status, exception.code(), exception.getMessage(), violations, request);
    }

    /**
     * Returns 409 with CONCURRENT_MODIFICATION for an optimistic write conflict.
     *
     * @param exception the failure being translated
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ApiError> handleOptimisticLock(
        OptimisticLockingFailureException exception,
        HttpServletRequest request
    ) {
        log.info("Concurrent modification detected: {}", exception.getClass().getSimpleName());
        return build(
            HttpStatus.CONFLICT,
            ErrorCode.CONCURRENT_MODIFICATION,
            "The resource was modified by another request. Reload it and try again.",
            List.of(),
            request
        );
    }

    /**
     * Returns 409 for a recognized named constraint; otherwise delegates to unexpected-failure handling.
     *
     * @param exception the failure being translated
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiError> handleDataIntegrity(
        DataIntegrityViolationException exception,
        HttpServletRequest request
    ) {
        return ConstraintViolationTranslator.translate(exception)
            .map(code -> {
                log.info("Database constraint rejected the write: code={}", code);
                return build(HttpStatus.CONFLICT, code, conflictMessage(code), List.<FieldViolation>of(), request);
            })
            .orElseGet(() -> handleUnexpected(exception, request));
    }

    /**
     * Logs the unexpected failure with its stack trace and returns a generic 500 body.
     *
     * <p>The client receives a request identifier for correlation rather than internal exception messages.
     *
     * @param exception the failure being translated
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error, requestId={}", currentRequestId(request), exception);
        return build(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCode.INTERNAL_ERROR,
            "Unexpected server error. Quote the requestId when reporting the problem.",
            List.of(),
            request
        );
    }

    /**
     * Converts invalid request-body fields into ordered violations, preserving framework status and headers.
     *
     * @param exception the failure being translated
     * @param headers the framework response headers to retain
     * @param status the selected HTTP status
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        List<FieldViolation> violations = exception
            .getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toViolation)
            .sorted(VIOLATION_ORDER)
            .toList();
        return body(headers, status, ErrorCode.VALIDATION_FAILED, "Request validation failed", violations, request);
    }

    /**
     * Converts method-parameter validation failures into ordered field violations.
     *
     * @param exception the failure being translated
     * @param headers the framework response headers to retain
     * @param status the selected HTTP status
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        List<FieldViolation> violations = exception
            .getParameterValidationResults()
            .stream()
            .flatMap(result ->
                result
                    .getResolvableErrors()
                    .stream()
                    .map(error ->
                        new FieldViolation(
                            result.getMethodParameter().getParameterName(),
                            simpleCode(error),
                            error.getDefaultMessage()
                        )
                    )
            )
            .sorted(VIOLATION_ORDER)
            .toList();
        return body(headers, status, ErrorCode.VALIDATION_FAILED, "Request validation failed", violations, request);
    }

    /**
     * Reports the parameter name and expected type without echoing the rejected input.
     *
     * @param exception the failure being translated
     * @param headers the framework response headers to retain
     * @param status the selected HTTP status
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
        TypeMismatchException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        String field = exception instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : exception.getPropertyName();
        String expected = exception.getRequiredType() == null ? "value" : exception.getRequiredType().getSimpleName();
        List<FieldViolation> violations = List.of(
            new FieldViolation(field, "TYPE_MISMATCH", "Expected a valid " + expected)
        );
        return body(
            headers,
            status,
            ErrorCode.INVALID_REQUEST,
            "Request parameter has invalid format",
            violations,
            request
        );
    }

    /**
     * Maps unreadable JSON into a safe format error without including request fragments.
     *
     * <p>When the direct cause is a Jackson exception with a path, exposes that path and distinguishes
     * unknown fields from invalid formats. Otherwise the field list is empty.
     *
     * @param exception the failure being translated
     * @param headers the framework response headers to retain
     * @param status the selected HTTP status
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
    ) {
        List<FieldViolation> violations = List.of();
        if (exception.getCause() instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
            String field = jackson
                .getPath()
                .stream()
                .map(reference ->
                    reference.getPropertyName() != null ? reference.getPropertyName() : "[" + reference.getIndex() + "]"
                )
                .collect(Collectors.joining("."));
            String code = jackson instanceof UnrecognizedPropertyException ? "UNKNOWN_FIELD" : "INVALID_FORMAT";
            violations = List.of(
                new FieldViolation(
                    field,
                    code,
                    "UNKNOWN_FIELD".equals(code) ? "Field is not supported" : "Value has invalid format or type"
                )
            );
        }
        return body(
            headers,
            status,
            ErrorCode.INVALID_REQUEST,
            "Malformed or unreadable request body",
            violations,
            request
        );
    }

    /**
     * Handles remaining framework failures while preserving supplied status and headers.
     *
     * <p>Returns no body for 406 because the response representation was not acceptable. Other responses
     * use ApiError. Framework server errors are logged with diagnostics.
     *
     * @param exception the failure being translated
     * @param body the framework-proposed response body, replaced by this override
     * @param headers the framework response headers to retain
     * @param statusCode the framework-selected HTTP status
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception exception,
        Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        if (statusCode.value() == HttpStatus.NOT_ACCEPTABLE.value()) {
            return ResponseEntity.status(statusCode).headers(headers).build();
        }
        if (statusCode.is5xxServerError()) {
            log.error("Framework error, requestId={}", MDC.get(RequestIdFilter.MDC_KEY), exception);
        }
        ErrorCode code =
            switch (statusCode.value()) {
                case 404 -> ErrorCode.NOT_FOUND;
                case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
                case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
                default -> statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
            };
        return body(headers, statusCode, code, safeFrameworkMessage(code), List.of(), request);
    }

    /**
     * Builds an ApiError response for a servlet-backed WebRequest, retaining supplied status and headers.
     *
     * @param headers the framework response headers to retain
     * @param status the selected HTTP status
     * @param code the stable application error identifier
     * @param message the client-safe explanation
     * @param violations the field violations, collected or rendered by this operation
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    private ResponseEntity<Object> body(
        HttpHeaders headers,
        HttpStatusCode status,
        ErrorCode code,
        String message,
        List<FieldViolation> violations,
        WebRequest request
    ) {
        HttpServletRequest servletRequest = ((ServletWebRequest) request).getRequest();
        ApiError error = apiError(status.value(), code, message, violations, servletRequest);
        return ResponseEntity.status(status).headers(headers).body(error);
    }

    /**
     * Builds a typed ApiError response with the selected application status.
     *
     * @param status the selected HTTP status
     * @param code the stable application error identifier
     * @param message the client-safe explanation
     * @param violations the field violations, collected or rendered by this operation
     * @param request the current servlet-backed request
     * @return the HTTP error response; the 406 framework path has no body
     */
    private ResponseEntity<ApiError> build(
        HttpStatus status,
        ErrorCode code,
        String message,
        List<FieldViolation> violations,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(status).body(apiError(status.value(), code, message, violations, request));
    }

    /**
     * Builds the body using the injected clock, URI path and current request identifier.
     *
     * @param status the selected HTTP status
     * @param code the stable application error identifier
     * @param message the client-safe explanation
     * @param violations the field violations, collected or rendered by this operation
     * @param request the current servlet-backed request
     * @return the safe API error body
     */
    private ApiError apiError(
        int status,
        ErrorCode code,
        String message,
        List<FieldViolation> violations,
        HttpServletRequest request
    ) {
        return new ApiError(
            Instant.now(clock),
            status,
            code.name(),
            message,
            request.getRequestURI(),
            currentRequestId(request),
            violations
        );
    }

    /**
     * Prefers the filter's request attribute, falling back to the thread logging context.
     *
     * @param request the current servlet-backed request
     * @return the request identifier, or null if neither source contains one
     */
    private static String currentRequestId(HttpServletRequest request) {
        Object fromFilter = request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE);
        return fromFilter != null ? fromFilter.toString() : MDC.get(RequestIdFilter.MDC_KEY);
    }

    /**
     * Maps application categories to their default statuses.
     *
     * <p>Specific framework protocol failures keep their supplied status through the framework overrides.
     *
     * @param category the application failure category
     * @return the default HTTP status for the category
     */
    static HttpStatus statusFor(ErrorCode.Category category) {
        return switch (category) {
            case INVALID_INPUT, PROTOCOL -> HttpStatus.BAD_REQUEST;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNEXPECTED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }

    /**
     * Converts a field error without retaining or exposing its rejected value.
     *
     * @param error the validation error whose safe metadata is read
     * @return the field metadata without a rejected value
     */
    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getCode(), error.getDefaultMessage());
    }

    /**
     * Selects the last, least-specific validation code, falling back to Invalid when no codes exist.
     *
     * @param error the validation error whose safe metadata is read
     * @return the least-specific code or Invalid
     */
    private static String simpleCode(MessageSourceResolvable error) {
        String[] codes = error.getCodes();
        return codes == null || codes.length == 0 ? "Invalid" : Arrays.asList(codes).getLast();
    }

    /**
     * Selects a client-safe message for recognized persistence conflicts.
     *
     * @param code the stable application error identifier
     * @return the client-safe explanation
     */
    private static String conflictMessage(ErrorCode code) {
        return switch (code) {
            case EMAIL_ALREADY_EXISTS -> "An account with this e-mail already exists";
            case ID_CARD_ALREADY_EXISTS -> "A patient with this identity document already exists";
            case USER_ALREADY_DOCTOR -> "This account is already assigned to a doctor";
            case RESOURCE_IN_USE -> "The resource is still referenced by other data";
            default -> "The request conflicts with the current state of the resource";
        };
    }

    /**
     * Selects a generic public message without exposing framework diagnostics.
     *
     * @param code the stable application error identifier
     * @return the client-safe explanation
     */
    private static String safeFrameworkMessage(ErrorCode code) {
        return switch (code) {
            case NOT_FOUND -> "No endpoint or resource at this path";
            case METHOD_NOT_ALLOWED -> "HTTP method is not supported for this path";
            case UNSUPPORTED_MEDIA_TYPE -> "Content-Type is not supported; use application/json";
            case INTERNAL_ERROR -> "Unexpected server error";
            default -> "Invalid request";
        };
    }

    /**
     * Orders field violations by nullable field name, then nullable code, independently of validator traversal order.
     */
    private static final Comparator<FieldViolation> VIOLATION_ORDER = Comparator.comparing(
        FieldViolation::field,
        Comparator.nullsFirst(Comparator.naturalOrder())
    ).thenComparing(FieldViolation::code, Comparator.nullsFirst(Comparator.naturalOrder()));
}
