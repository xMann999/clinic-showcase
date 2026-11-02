package com.example.clinic.facade;

import com.example.clinic.dto.DoctorSummaryDto;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Captures a booking performed inside a transaction; publication does not prove commit.
 *
 * <p>All components are immutable snapshots, with no patient details or persistence proxies.
 * Cancellation does not revise this historical fact. A later booking has a new event identifier.
 *
 * @param eventId unique identity of this booking operation
 * @param userId recipient account, not an authorization credential
 * @param visitId booked slot identifier
 * @param dateTime local appointment start in {@code zoneId}, retaining the slot's wall-clock semantics
 * @param zoneId clinic zone at booking time; no offset is inferred for ambiguous local times
 * @param durationInMinutes appointment duration
 * @param doctor doctor display snapshot already loaded for the booking summary
 * @param occurredAt time of publication from the application's clock, before commit
 */
public record VisitBookedEvent(
    UUID eventId,
    Long userId,
    Long visitId,
    LocalDateTime dateTime,
    String zoneId,
    int durationInMinutes,
    DoctorSummaryDto doctor,
    Instant occurredAt
) {}
