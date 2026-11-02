package com.example.clinic.repository;

import com.example.clinic.domain.Visit;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * Builds composable database predicates for visit searches.
 *
 * <p>{@code matching} adds only supplied criteria and combines them with AND using {@code allOf}.
 * An absent patient filter adds no predicate; availability explicitly chooses IS NULL or IS NOT NULL.
 * The service validates time-bound order. These helpers do not query, lock or validate identifiers.
 */
public final class VisitSpecifications {

    private VisitSpecifications() {}

    /**
     * Combines all supplied criteria with AND; absent criteria contribute no restriction.
     *
     * @param criteria the nonnull filter record
     * @return the composed specification
     */
    public static Specification<Visit> matching(VisitSearchCriteria criteria) {
        List<Specification<Visit>> parts = new ArrayList<>();
        if (criteria.doctorId() != null) {
            parts.add(forDoctor(criteria.doctorId()));
        }
        if (criteria.patientId() != null) {
            parts.add(forPatient(criteria.patientId()));
        }
        if (criteria.from() != null) {
            parts.add(startingAtOrAfter(criteria.from()));
        }
        if (criteria.to() != null) {
            parts.add(startingBefore(criteria.to()));
        }
        if (criteria.available() != null) {
            parts.add(available(criteria.available()));
        }
        return Specification.allOf(parts);
    }

    /**
     * Selects visits referring to the supplied doctor identifier.
     *
     * @param doctorId the doctor identifier
     * @return the doctor predicate
     */
    public static Specification<Visit> forDoctor(Long doctorId) {
        return (root, query, cb) -> cb.equal(root.get("doctor").get("id"), doctorId);
    }

    /**
     * Selects visits referring to the supplied patient identifier.
     *
     * @param patientId the patient identifier
     * @return the patient predicate
     */
    public static Specification<Visit> forPatient(Long patientId) {
        return (root, query, cb) -> cb.equal(root.get("patient").get("id"), patientId);
    }

    /**
     * Builds an inclusive lower bound on the visit start.
     *
     * @param from the inclusive local time
     * @return the lower-bound specification
     */
    public static Specification<Visit> startingAtOrAfter(LocalDateTime from) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("dateTime"), from);
    }

    /**
     * Builds an exclusive upper bound on the visit start.
     *
     * @param to the exclusive local bound
     * @return the upper-bound predicate
     */
    public static Specification<Visit> startingBefore(LocalDateTime to) {
        return (root, query, cb) -> cb.lessThan(root.get("dateTime"), to);
    }

    /**
     * Selects free slots through IS NULL or booked slots through IS NOT NULL.
     *
     * @param available true for free, false for booked
     * @return the availability predicate
     */
    public static Specification<Visit> available(boolean available) {
        return (root, query, cb) -> available ? cb.isNull(root.get("patient")) : cb.isNotNull(root.get("patient"));
    }
}
