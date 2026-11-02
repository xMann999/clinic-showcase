package com.example.clinic.repository;

/**
 * Exposes a name-only interface projection for patient queries.
 *
 * <p>Spring Data supplies the projection implementation for the declared accessors. Consumers receive
 * selected identity/name values rather than a Patient with editable state or ownership relationships.
 */
public interface PatientNameView {
    Long getId();

    String getFirstName();

    String getLastName();
}
