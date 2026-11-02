package com.example.clinic.error;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Describes an input field problem without retaining the rejected value.
 *
 * <p>Rejected values may contain passwords or identity documents. The code identifies the constraint
 * or binding problem; the message explains it. Scalar components are immutable, but this record performs
 * no validation or sanitization of caller-supplied strings.
 *
 * @param field the field path or parameter name, possibly null for unnamed errors
 * @param code the constraint or binding error identifier
 * @param message the explanation without the rejected value
 */
@Schema(description = "Single field problem")
public record FieldViolation(
    @Schema(example = "lastName") String field,
    @Schema(example = "NotBlank") String code,
    @Schema(example = "must not be blank") String message
) {}
