package com.example.clinic.dto;

import java.util.List;

/**
 * Carries doctor detail, account identifier and facilities for API output.
 *
 * <p>The mapper orders facilities by identifier; list endpoints use DoctorListItemDto without the collection.
 * The record retains its supplied list reference rather than copying it, so construction alone does not
 * ensure deep immutability. The normal mapping path supplies an unmodifiable stream list.
 *
 * @param id the resource identifier
 * @param firstName the first name
 * @param lastName the surname
 * @param fullName the derived display name
 * @param specialization the doctor's specialization
 * @param userId the account identifier
 * @param facilities the facility representations; the supplied list is retained
 */
public record DoctorDto(
    Long id,
    String firstName,
    String lastName,
    String fullName,
    String specialization,
    Long userId,
    List<FacilityDto> facilities
) {}
