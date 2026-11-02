package com.example.clinic.error;

/**
 * Represents an application rule failure using a stable code and client-safe message.
 *
 * <p>The MVC advice maps the code category to a status and exposes the message. Callers must therefore
 * avoid putting credentials, documents or database diagnostics into the message; the exception itself
 * does not sanitize it. As an unchecked exception it participates in Spring's default rollback rules.
 * The constructor does not preserve an underlying cause.
 */
public class DomainException extends RuntimeException {

    private final ErrorCode code;

    public DomainException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the stable application failure code.
     *
     * @return the code supplied by the caller
     */
    public ErrorCode code() {
        return code;
    }

    /**
     * Creates a missing-resource failure using only the resource label and identifier.
     *
     * @param code the appropriate not-found code
     * @param resource the client-safe resource label
     * @param id the missing identifier
     * @return the domain failure; this factory does not throw it
     */
    public static DomainException notFound(ErrorCode code, String resource, Object id) {
        return new DomainException(code, resource + " with id " + id + " was not found");
    }
}
