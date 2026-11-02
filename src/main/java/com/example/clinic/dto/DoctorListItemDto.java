package com.example.clinic.dto;

/**
 * Carries a doctor's identifier, derived name, specialization and account identifier for a list.
 *
 * <p>Omitting facilities avoids collection traversal during paged mapping and keeps list queries independent
 * of collection fetching. The components are immutable scalar values.
 *
 * @param id the resource identifier
 * @param fullName the derived display name
 * @param specialization the doctor's specialization
 * @param userId the account identifier
 */
public record DoctorListItemDto(Long id, String fullName, String specialization, Long userId) {}
