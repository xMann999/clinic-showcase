package com.example.clinic.repository;

import com.example.clinic.domain.Patient;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists patients and supplies filtered queries and projections.
 *
 * <h2>Responsibility</h2>
 * <p>Declares persistence operations implemented by Spring Data repository proxies; inherited
 * JpaRepository methods provide standard entity lookup, saving, paging and deletion.
 *
 * <h2>How it works</h2>
 * <p>Derived queries traverse properties; explicit JPQL names entities and fields. The native count
 * uses table/column names. Projections return selected values rather than mutable patient entities.
 *
 * <h2>Design choices</h2>
 * <p>Projection queries avoid loading full personal data when only names are needed.
 *
 * <h2>Important details</h2>
 * <p>The bulk phone update requires a caller-owned write transaction and clears the persistence context.
 * Empty queries return empty collections/pages or Optional values as declared. Database constraints
 * remain necessary; existence queries alone cannot make a subsequent write atomic.
 */
public interface PatientRepository extends JpaRepository<Patient, Long> {
    /**
     * Pages patients by exact owning-account email through {@code patient.user.email}.
     *
     * @param email the email already normalized by the caller
     * @param pageable the requested page and sorting
     * @return the matching patient page
     */
    Page<Patient> findAllByUserEmail(String email, Pageable pageable);

    /**
     * Pages patients whose last name contains the supplied fragment, ignoring case.
     *
     * @param lastNameFragment the surname fragment
     * @param pageable the requested page and sorting
     * @return the matching patient page
     */
    Page<Patient> findAllByLastNameContainingIgnoreCase(String lastNameFragment, Pageable pageable);

    /**
     * Finds birthdays within inclusive bounds and orders results by last name ascending.
     *
     * @param from the inclusive lower date
     * @param to the inclusive upper date
     * @return matching patients; ties have no additional declared order
     */
    List<Patient> findAllByBirthdayBetweenOrderByLastNameAsc(LocalDate from, LocalDate to);

    /**
     * Finds patients born strictly before the date with a case-insensitive surname fragment.
     *
     * @param date the exclusive upper birth date
     * @param lastNameFragment the surname fragment
     * @return all matches without pagination or declared order
     */
    List<Patient> findAllByBirthdayBeforeAndLastNameContainingIgnoreCase(LocalDate date, String lastNameFragment);

    /**
     * Checks document reuse without loading a full patient.
     *
     * @param idCardNo the document number
     * @return whether a match exists; concurrent writers still require a unique constraint
     */
    boolean existsByIdCardNo(String idCardNo);

    /**
     * Checks whether the account owns any patients.
     *
     * @param userId the account identifier
     * @return whether at least one patient refers to that account
     */
    boolean existsByUserId(Long userId);

    /**
     * Returns name-only interface projections ordered by patient identifier.
     *
     * @param userId the owner identifier
     * @return the projected names, empty when none match
     */
    List<PatientNameView> findAllProjectedByUserIdOrderByIdAsc(Long userId);

    /**
     * Returns constructor projections using a named JPQL parameter.
     *
     * <p>The owner value is bound as a query parameter rather than concatenated into the query text.
     *
     * @param userId the owner identifier
     * @return identifier/surname rows in ascending identifier order
     */
    @Query(
        "select new com.example.clinic.repository.PatientNameRow(p.id, p.lastName) " +
        "from Patient p where p.user.id = :userId order by p.id"
    )
    List<PatientNameRow> findNameRowsByUserId(@Param("userId") Long userId);

    /**
     * Clears all patient phone numbers for an account with a bulk JPQL update.
     *
     * <p>Requires a surrounding write transaction. Automatic flush synchronizes pending changes first;
     * automatic clear then detaches entities so later loads can observe the bulk result. Existing object
     * references are not rewritten and must not be treated as managed after clearing.
     *
     * @param userId the account identifier
     * @return the number of updated rows
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Patient p set p.phoneNumber = null where p.user.id = :userId")
    int clearPhoneNumbersOfUser(@Param("userId") Long userId);

    /**
     * Counts null phone values using native SQL table and column names.
     *
     * @return the number of patients without a stored phone
     */
    @Query(value = "select count(*) from patients where phone_number is null", nativeQuery = true)
    long countWithoutPhoneNative();
}
