package com.example.clinic.dto;

/**
 * Carries a doctor's identifier, derived full name and specialization inside a booking response.
 *
 * <p>Contains no account entity or facilities; the scalar components are immutable.
 *
 * @param id the resource identifier
 * @param fullName the derived display name
 * @param specialization the doctor's specialization
 */
public record DoctorSummaryDto(Long id, String fullName, String specialization) {}
