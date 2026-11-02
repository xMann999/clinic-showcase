package com.example.clinic.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable historical receipt of a committed booking, not the visit's current status.
 *
 * <p>Contains no patient or account details. Cancellation leaves the receipt unchanged.
 *
 * @param eventId confirmation identity, also used for bounded deduplication
 * @param visitId booked slot identifier
 * @param dateTime local appointment start, interpreted in {@code zoneId} without resolving DST ambiguity
 * @param zoneId clinic zone captured at booking time
 * @param durationInMinutes appointment duration
 * @param doctor immutable doctor display snapshot
 * @param createdAt confirmation creation instant from the application clock after commit
 */
public record BookingConfirmationDto(
    UUID eventId,
    Long visitId,
    LocalDateTime dateTime,
    String zoneId,
    int durationInMinutes,
    DoctorSummaryDto doctor,
    Instant createdAt
) {}
