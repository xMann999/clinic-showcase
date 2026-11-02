package com.example.clinic.dto;

import java.time.LocalDate;

/**
 * Carries the public patient profile without the identity document.
 *
 * <p>Full name is derived by the mapper; ownership is represented only by userId. Birthday is a date
 * without a time zone. Null phone means no phone is stored. The components are immutable scalar/date values.
 *
 * @param id the resource identifier
 * @param firstName the first name
 * @param lastName the surname
 * @param fullName the derived display name
 * @param birthday the birth date without a time zone
 * @param phoneNumber the phone number, or null for absence
 * @param userId the account identifier
 */
public record PatientDto(
    Long id,
    String firstName,
    String lastName,
    String fullName,
    LocalDate birthday,
    String phoneNumber,
    Long userId
) {}
