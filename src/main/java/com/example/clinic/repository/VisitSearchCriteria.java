package com.example.clinic.repository;

import java.time.LocalDateTime;

/**
 * Carries optional filters for database-side visit searches.
 *
 * <p>Null means no predicate for that component. Local times have no offset and follow the clinic
 * zone contract; the service checks increasing bounds when both are supplied. The record components
 * are immutable values. Supplying contradictory patient/availability filters may produce no matches.
 *
 * @param doctorId the optional doctor identifier
 * @param patientId the optional patient identifier
 * @param from the inclusive local start bound
 * @param to the exclusive local start bound
 * @param available true for free slots, false for booked slots, null for either
 */
public record VisitSearchCriteria(
    Long doctorId,
    Long patientId,
    LocalDateTime from,
    LocalDateTime to,
    Boolean available
) {}
