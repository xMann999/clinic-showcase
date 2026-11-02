package com.example.clinic.error;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Defines stable client error identifiers grouped by application failure category.
 *
 * <p>Clients should inspect these codes rather than message text. Categories describe domain meaning;
 * GlobalExceptionHandler supplies HTTP translation, leaving HTTP classes out of this enum. Storing an
 * account role and defining error categories do not themselves implement access control.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public enum ErrorCode {
    VALIDATION_FAILED(Category.INVALID_INPUT),
    INVALID_REQUEST(Category.INVALID_INPUT),
    VISIT_IN_PAST(Category.INVALID_INPUT),
    VISIT_INVALID_TIME(Category.INVALID_INPUT),

    USER_NOT_FOUND(Category.NOT_FOUND),
    PATIENT_NOT_FOUND(Category.NOT_FOUND),
    DOCTOR_NOT_FOUND(Category.NOT_FOUND),
    FACILITY_NOT_FOUND(Category.NOT_FOUND),
    VISIT_NOT_FOUND(Category.NOT_FOUND),
    BOOKING_NOT_FOUND(Category.NOT_FOUND),
    NOT_FOUND(Category.NOT_FOUND),

    EMAIL_ALREADY_EXISTS(Category.CONFLICT),
    ID_CARD_ALREADY_EXISTS(Category.CONFLICT),
    USER_ALREADY_DOCTOR(Category.CONFLICT),
    VISIT_ALREADY_BOOKED(Category.CONFLICT),
    VISIT_OVERLAP(Category.CONFLICT),
    OWNERSHIP_MISMATCH(Category.CONFLICT),
    CONCURRENT_MODIFICATION(Category.CONFLICT),
    RESOURCE_IN_USE(Category.CONFLICT),

    METHOD_NOT_ALLOWED(Category.PROTOCOL),
    UNSUPPORTED_MEDIA_TYPE(Category.PROTOCOL),
    NOT_ACCEPTABLE(Category.PROTOCOL),

    INTERNAL_ERROR(Category.UNEXPECTED);

    /** Groups failure meanings for web-layer translation without storing an HTTP status in the domain. */
    public enum Category {
        INVALID_INPUT,
        NOT_FOUND,
        CONFLICT,
        PROTOCOL,
        UNEXPECTED,
    }

    private final Category category;

    /**
     * Returns the category used by web-layer status translation.
     *
     * @return the application failure category
     */
    public Category category() {
        return category;
    }
}
