package com.example.clinic.domain;

import java.util.Locale;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Normalizes account emails for consistent lookup and storage.
 *
 * <p>Uses {@code strip()} and lowercase with {@link Locale#ROOT}, avoiding default-locale casing differences.
 * This is the project's whole-address normalization policy, not a general email-validity check.
 * Database uniqueness applies to the stored value; this helper neither queries nor locks the database.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class EmailAddress {

    /**
     * Strips surrounding whitespace and applies locale-independent lowercase.
     *
     * @param rawEmail the nonnull email text; format validation belongs to the input boundary
     * @return the normalized address
     * @throws NullPointerException if rawEmail is null
     */
    public static String normalize(String rawEmail) {
        return rawEmail.strip().toLowerCase(Locale.ROOT);
    }
}
