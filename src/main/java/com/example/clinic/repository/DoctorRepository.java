package com.example.clinic.repository;

import com.example.clinic.domain.Doctor;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists doctor profiles and supplies relationship checks and slot-creation locking.
 *
 * <h2>Responsibility</h2>
 * <p>Spring Data implements this interface and inherited entity CRUD and paging operations.
 *
 * <h2>How it works</h2>
 * <p>Derived queries traverse account and facility identifiers. An entity graph requests facilities
 * for detail mapping; the explicit JPQL lookup requests a pessimistic lock on an existing doctor.
 *
 * <h2>Design choices</h2>
 * <p>Locking the doctor provides a shared row even when no visits exist. A surrounding transaction
 * must retain that lock through the service's slot lookup, overlap check and insertion.
 *
 * <h2>Important details</h2>
 * <p>Optional lookups represent absence. Existence queries do not reserve uniqueness, and direct
 * slot inserts can bypass the cooperative locking protocol. Lock behavior depends on the database.
 */
public interface DoctorRepository extends JpaRepository<Doctor, Long> {
    /**
     * Locks an existing doctor row for cooperating slot creators.
     *
     * <p>The JPQL query names the entity and identifier property; {@code PESSIMISTIC_WRITE} requests a
     * write lock for the surrounding transaction. Locking a parent works even when it has no slots.
     * Callers must hold a transaction and read/check slots after acquiring it. The database determines
     * lock waiting and failure behavior. Direct inserts that skip this protocol are not protected.
     *
     * @param id the doctor identifier
     * @return the locked doctor, or empty if missing
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Doctor d where d.id = :id")
    Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

    /**
     * Loads doctor detail with facilities requested by an entity graph.
     *
     * <p>The graph requests relationship fetching; it does not itself specify the provider's SQL join shape.
     *
     * @param id the doctor identifier
     * @return the fetched doctor, or empty if missing
     */
    @EntityGraph(attributePaths = "facilities")
    Optional<Doctor> findWithFacilitiesById(Long id);

    /**
     * Tests whether any doctor refers to the account.
     *
     * @param userId the account identifier
     * @return whether a matching profile exists; not an atomic uniqueness reservation
     */
    boolean existsByUserId(Long userId);

    /**
     * Tests whether any doctor is associated with the facility.
     *
     * <p>The derived property path traverses the many-to-many facilities association.
     *
     * @param facilityId the facility identifier
     * @return whether at least one matching association exists
     */
    boolean existsByFacilitiesId(Long facilityId);
}
