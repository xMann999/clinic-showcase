package com.example.clinic.error;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Recognizes named database constraints in persistence failures.
 *
 * <h2>Responsibility</h2>
 * <p>Maps a fixed allowlist of constraint names to stable application error codes.
 *
 * <h2>How it works</h2>
 * <p>Walks the cause chain, collecting Hibernate constraint names and exception messages, then performs
 * case-insensitive substring matching against the configured names.
 *
 * <h2>Design choices</h2>
 * <p>Database constraints close races left by service pre-checks. An unrecognized integrity violation
 * is left for server-error handling rather than mislabeled as an email duplicate.
 *
 * <h2>Important details</h2>
 * <p>Collected diagnostics are used for recognition, not returned to clients. Matching depends on the
 * driver/provider exposing the constraint name; different database messages may remain unrecognized.
 */
public final class ConstraintViolationTranslator {

    private static final Map<String, ErrorCode> KNOWN_CONSTRAINTS = Map.of(
        "uk_users_email",
        ErrorCode.EMAIL_ALREADY_EXISTS,
        "uk_patients_id_card_no",
        ErrorCode.ID_CARD_ALREADY_EXISTS,
        "uk_doctors_user_id",
        ErrorCode.USER_ALREADY_DOCTOR,
        "fk_patients_user",
        ErrorCode.RESOURCE_IN_USE,
        "fk_doctors_user",
        ErrorCode.RESOURCE_IN_USE,
        "fk_visits_doctor",
        ErrorCode.RESOURCE_IN_USE,
        "fk_visits_patient",
        ErrorCode.RESOURCE_IN_USE,
        "fk_doctor_facility_facility",
        ErrorCode.RESOURCE_IN_USE
    );

    private ConstraintViolationTranslator() {}

    /**
     * Recognizes an allowlisted constraint name in the exception chain.
     *
     * @param exception the nonnull persistence failure
     * @return the corresponding code, or empty when no known name is found
     */
    public static Optional<ErrorCode> translate(DataIntegrityViolationException exception) {
        String details = collectMessages(exception).toLowerCase(Locale.ROOT);
        return KNOWN_CONSTRAINTS.entrySet()
            .stream()
            .filter(entry -> details.contains(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst();
    }

    /**
     * Collects constraint names and messages through the cause chain for internal recognition.
     *
     * <p>Stops a direct self-cause. It does not detect a longer cyclic cause chain; collected text is
     * not suitable for client output.
     *
     * @param throwable the failure whose cause chain is inspected
     * @return the derived text
     */
    private static String collectMessages(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (
                current instanceof org.hibernate.exception.ConstraintViolationException hibernate &&
                hibernate.getConstraintName() != null
            ) {
                builder.append(hibernate.getConstraintName()).append(' ');
            }
            if (current.getMessage() != null) {
                builder.append(current.getMessage()).append(' ');
            }
            if (current.getCause() == current) {
                break;
            }
        }
        return builder.toString();
    }
}
