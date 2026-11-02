package com.example.clinic.dto;

import java.time.LocalDateTime;

/**
 * Carries a visit's identifiers, local start and duration in minutes for API output.
 *
 * <p>A null patientId means a free slot. The local start has no offset and follows the configured clinic
 * zone, default Europe/Warsaw. No patient or doctor entity is serialized. The components are immutable values.
 *
 * @param id the resource identifier
 * @param doctorId the doctor identifier
 * @param patientId the patient identifier; null in a free-slot response
 * @param dateTime the local clinic start without an offset
 * @param durationInMinutes the duration in minutes
 */
public record VisitDto(Long id, Long doctorId, Long patientId, LocalDateTime dateTime, int durationInMinutes) {}
