package com.example.clinic.domain;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a half-open local-time interval {@code [start, end)}.
 *
 * <p>Both boundaries are required and the end must follow the start. Record equality compares the
 * boundary values, unlike entities whose equality uses database identity. The immutable local-date-time
 * components contain no offset or zone; this value does not itself resolve daylight-saving transitions.
 *
 * @param start the inclusive local start
 * @param end the exclusive local end
 */
public record TimeRange(LocalDateTime start, LocalDateTime end) {
    /**
     * Requires nonnull boundaries with the end strictly after the start.
     *
     * @param start the inclusive local start
     * @param end the exclusive local end
     * @throws NullPointerException if either boundary is null
     * @throws IllegalArgumentException if the end does not follow the start
     */
    public TimeRange {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("Time range end must be after start");
        }
    }

    /**
     * Builds an interval by adding minutes to the supplied start.
     *
     * @param start the nonnull inclusive local start
     * @param durationInMinutes the positive duration
     * @return the constructed half-open interval
     * @throws IllegalArgumentException if the calculated end does not follow the start
     */
    public static TimeRange of(LocalDateTime start, int durationInMinutes) {
        return new TimeRange(start, start.plusMinutes(durationInMinutes));
    }

    /**
     * Tests whether each interval starts before the other's end.
     *
     * <p>Touching intervals such as 10:00-10:30 and 10:30-11:00 do not overlap.
     *
     * @param other the nonnull interval to compare
     * @return whether the intervals overlap
     */
    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }

    /**
     * Calculates elapsed local time between the interval boundaries.
     *
     * @return the positive duration; no zone-transition adjustment is applied
     */
    public Duration duration() {
        return Duration.between(start, end);
    }
}
