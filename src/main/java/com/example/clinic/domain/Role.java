package com.example.clinic.domain;

/**
 * Classifies an account as USER or ADMIN using names persisted as strings.
 *
 * <p>A doctor profile is a separate relationship rather than a role, so an account may also own patients.
 * These values alone do not implement authorization; the application has no login module.
 */
public enum Role {
    USER,
    ADMIN,
}
