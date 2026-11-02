package com.example.clinic.dto;

/**
 * Carries a patient's identifier and derived name inside a booking response.
 *
 * <p>Excludes the identity document, birthday and phone. Both components are immutable values.
 *
 * @param id the resource identifier
 * @param fullName the derived display name
 */
public record PatientSummaryDto(Long id, String fullName) {}
