package com.example.clinic.error;

import java.util.List;

/**
 * Reports structured input errors detected by application code rather than constraint annotations.
 *
 * <p>Uses VALIDATION_FAILED and the same FieldViolation shape as Bean Validation errors. The constructor
 * copies the supplied nonnull list into an unmodifiable list. The MVC advice exposes that list and the
 * message; callers must supply client-safe text without rejected sensitive values.
 */
public class RequestValidationException extends DomainException {

    private final List<FieldViolation> violations;

    public RequestValidationException(String message, List<FieldViolation> violations) {
        super(ErrorCode.VALIDATION_FAILED, message);
        this.violations = List.copyOf(violations);
    }

    /**
     * Creates a validation exception for one field without retaining the rejected value.
     *
     * @param field the field name
     * @param code the validation identifier
     * @param message the client-safe explanation
     * @return the exception containing a single violation
     */
    public static RequestValidationException single(String field, String code, String message) {
        return new RequestValidationException(message, List.of(new FieldViolation(field, code, message)));
    }

    /**
     * Returns the immutable copy of the field violations.
     *
     * @return the unmodifiable violation list
     */
    public List<FieldViolation> violations() {
        return violations;
    }
}
