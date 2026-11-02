package com.example.clinic.error;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/**
 * Defines the error body built by the clinic's MVC exception advice.
 *
 * <p>Clients branch on the stable code rather than parsing the human-readable message. Path excludes
 * query parameters and request bodies. requestId correlates the response with request logging when
 * available. The constructor converts null errors to an empty list and otherwise copies the list.
 * This contract does not cover all servlet/container failures; a 406 response deliberately has no body.
 *
 * @param timestamp the instant from the injected clock
 * @param status the HTTP status value
 * @param code the stable error identifier
 * @param message the client-safe explanation
 * @param path the request URI path
 * @param requestId the correlation identifier, possibly null outside the filter path
 * @param errors the field violations, normalized to an unmodifiable nonnull list
 */
@Schema(description = "Error response returned by every failing endpoint")
public record ApiError(
    Instant timestamp,
    int status,
    @Schema(example = "VALIDATION_FAILED") String code,
    String message,
    @Schema(example = "/api/patients") String path,
    String requestId,
    List<FieldViolation> errors
) {
    public ApiError {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }
}
