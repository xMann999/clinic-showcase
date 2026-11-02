package com.example.clinic.repository;

/**
 * Carries the identifier and surname selected by a JPQL constructor expression.
 *
 * <p>This record is a projection, not a managed patient entity; changing persistence state requires
 * an entity operation. Both components are immutable values.
 *
 * @param id the patient identifier
 * @param lastName the selected surname
 */
public record PatientNameRow(Long id, String lastName) {}
