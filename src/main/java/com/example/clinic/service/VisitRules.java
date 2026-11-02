package com.example.clinic.service;

import com.example.clinic.domain.TimeRange;
import com.example.clinic.domain.Visit;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Evaluates appointment-time rules using the injected clock.
 *
 * <h2>Responsibility</h2>
 * <p>Checks slot boundaries, duration, past times and overlap. {@link VisitService} uses these rules
 * for slot creation; {@link com.example.clinic.facade.BookingFacade} uses the past-time check before
 * booking. Those operations own persistence and concurrency control; this component neither loads
 * entities nor opens transactions.
 *
 * <h2>How it works</h2>
 * <p>Interprets local times in the clock's configured zone and compares past starts as instants.
 *
 * <h2>Design choices</h2>
 * <p>An injected clock keeps the source of time and zone explicit. Half-open intervals allow adjacent slots.
 *
 * <h2>Important details</h2>
 * <p>Only slot creation checks that both endpoints have exactly one zone offset. A slot ending at
 * midnight is rejected because its end has the next calendar date.
 */
@Component
@RequiredArgsConstructor
public class VisitRules {

    public static final int SLOT_STEP_MINUTES = 15;
    public static final int MAX_DURATION_MINUTES = 240;

    private final Clock clock;

    /**
     * Validates a new slot and returns its half-open interval.
     *
     * <p>Requires a full quarter-hour, a positive multiple-of-15 duration no greater than 240 minutes,
     * same-day ending and unambiguous endpoints. Midnight endings fail the same-day rule. Checks the clock
     * last. These checks also protect service calls that bypass HTTP validation.
     *
     * @param start the nonnull local clinic start
     * @param durationInMinutes the requested duration
     * @return the validated interval
     * @throws DomainException if the time or duration violates a rule or starts in the past
     */
    public TimeRange validateNewSlot(LocalDateTime start, int durationInMinutes) {
        if (start.getMinute() % SLOT_STEP_MINUTES != 0 || start.getSecond() != 0 || start.getNano() != 0) {
            throw invalidTime("Visit must start at a full quarter of an hour");
        }
        if (
            durationInMinutes <= 0 ||
            durationInMinutes % SLOT_STEP_MINUTES != 0 ||
            durationInMinutes > MAX_DURATION_MINUTES
        ) {
            throw invalidTime("Duration must be a positive multiple of 15 minutes, at most 240");
        }
        TimeRange range = TimeRange.of(start, durationInMinutes);

        if (!range.end().toLocalDate().equals(start.toLocalDate())) {
            throw invalidTime("Visit must end on the same calendar day");
        }
        requireUnambiguousLocalTime(range.start());
        requireUnambiguousLocalTime(range.end());
        ensureNotInPast(start);
        return range;
    }

    /**
     * Rejects a start whose resolved instant is before the injected clock's current instant.
     *
     * <p>This operation does not itself reject daylight-saving gaps or overlaps; new-slot validation
     * performs that check separately before reaching this comparison.
     *
     * @param start the nonnull local start interpreted in the clock zone
     * @throws DomainException if the resolved start is in the past
     */
    public void ensureNotInPast(LocalDateTime start) {
        if (start.atZone(zone()).toInstant().isBefore(clock.instant())) {
            throw new DomainException(ErrorCode.VISIT_IN_PAST, "Visit time " + start + " is in the past");
        }
    }

    /**
     * Tests overlap against the visits supplied by the caller.
     *
     * <p>The slot-creation caller restricts the collection to the doctor and day. This helper does not
     * query the database or filter out visits belonging to another doctor.
     *
     * @param candidate the proposed half-open interval
     * @param sameDayVisits the visits to compare
     * @return whether any supplied visit overlaps the candidate
     */
    public boolean collidesWithAny(TimeRange candidate, Collection<Visit> sameDayVisits) {
        return sameDayVisits.stream().map(Visit::timeRange).anyMatch(candidate::overlaps);
    }

    /**
     * Rejects a local endpoint skipped or repeated by a zone transition.
     *
     * @param localDateTime the endpoint to resolve
     * @throws DomainException if the zone has zero or multiple valid offsets
     */
    private void requireUnambiguousLocalTime(LocalDateTime localDateTime) {
        int validOffsets = zone().getRules().getValidOffsets(localDateTime).size();
        if (validOffsets != 1) {
            throw invalidTime("Local time " + localDateTime + " is skipped or repeated by a daylight saving change");
        }
    }

    /**
     * Returns the zone of the injected clock for all local-time calculations.
     *
     * @return the clock's zone
     */
    private ZoneId zone() {
        return clock.getZone();
    }

    /**
     * Builds a client-facing invalid-time failure with the supplied explanation.
     *
     * @param message the client-safe rule explanation
     * @return the domain exception
     */
    private static DomainException invalidTime(String message) {
        return new DomainException(ErrorCode.VISIT_INVALID_TIME, message);
    }
}
