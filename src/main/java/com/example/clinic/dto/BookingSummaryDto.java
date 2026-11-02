package com.example.clinic.dto;

import java.time.LocalDateTime;

/**
 * Summarizes a booking with visit time and name-only patient/doctor views.
 *
 * <p>The facade builds it before transaction completion while lazy names are accessible; open-session-in-view
 * is disabled. The local start has no offset and follows the clinic zone. Mapping a free slot can produce
 * a null patient summary; successful facade booking lookup requires a matching booked patient.
 *
 * @param visitId the visit identifier
 * @param dateTime the local clinic start without an offset
 * @param durationInMinutes the duration in minutes
 * @param patient the patient summary, or null when mapping a free slot
 * @param doctor the required doctor summary
 */
public record BookingSummaryDto(
    Long visitId,
    LocalDateTime dateTime,
    int durationInMinutes,
    PatientSummaryDto patient,
    DoctorSummaryDto doctor
) {}
