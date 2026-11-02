package com.example.clinic.repository;

import com.example.clinic.domain.Visit;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists visit slots and executes dynamic search specifications.
 *
 * <h2>Responsibility</h2>
 * <p>Declares persistence operations implemented by Spring Data repository proxies; inherited
 * JpaRepository methods provide standard entity lookup, saving, paging and deletion.
 *
 * <h2>How it works</h2>
 * <p>JpaSpecificationExecutor supplies specification-based paging. Custom JPQL restricts time ranges
 * and fetches details; count and existence methods derive predicates from relationships.
 *
 * <h2>Design choices</h2>
 * <p>Database-side time filtering limits the collection used for overlap checks.
 *
 * <h2>Important details</h2>
 * <p>The fetch query uses an inner doctor join and a left patient join so free slots remain visible.
 * Empty queries return empty collections/pages or Optional values as declared. Database constraints
 * remain necessary; existence queries alone cannot make a subsequent write atomic.
 */
public interface VisitRepository extends JpaRepository<Visit, Long>, JpaSpecificationExecutor<Visit> {
    /**
     * Loads a doctor's slots whose start lies in {@code [from, to)}, ordered by start.
     *
     * <p>The creation caller supplies one day because service-created slots cannot cross midnight.
     * This query alone neither locks the doctor nor excludes concurrent insertions.
     *
     * @param doctorId the doctor identifier
     * @param from the inclusive local start bound
     * @param to the exclusive local start bound
     * @return matching visits ordered by start, without a declared tie-breaker
     */
    @Query(
        "select v from Visit v where v.doctor.id = :doctorId " +
        "and v.dateTime >= :from and v.dateTime < :to order by v.dateTime"
    )
    List<Visit> findDoctorVisitsBetween(
        @Param("doctorId") Long doctorId,
        @Param("from") LocalDateTime from,
        @Param("to") LocalDateTime to
    );

    /**
     * Fetches a visit with its required doctor and optional patient.
     *
     * <p>The left patient fetch join retains free slots rather than filtering out null patient values.
     *
     * @param id the visit identifier
     * @return the visit with fetched details, or empty if missing
     */
    @Query("select v from Visit v join fetch v.doctor left join fetch v.patient where v.id = :id")
    Optional<Visit> findWithDoctorAndPatientById(@Param("id") Long id);

    /**
     * Tests whether any visit refers to the doctor.
     *
     * @param doctorId the doctor identifier
     * @return whether any matching visit exists
     */
    boolean existsByDoctorId(Long doctorId);

    /**
     * Tests whether any visit refers to the patient.
     *
     * @param patientId the patient identifier
     * @return whether any matching visit exists
     */
    boolean existsByPatientId(Long patientId);

    /**
     * Counts all free and booked slots belonging to the doctor.
     *
     * @param doctorId the doctor identifier
     * @return the matching slot count
     */
    long countByDoctorId(Long doctorId);
}
