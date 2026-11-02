package com.example.clinic.domain;

import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents either a free doctor slot or a patient booking.
 *
 * <h2>Responsibility</h2>
 * <p>Stores the doctor and local start/duration and controls a single slot's booking state.
 *
 * <h2>How it works</h2>
 * <p>A null patient foreign key means available. Booking assigns a patient; cancellation clears it.
 * The end is derived from the stored start and duration rather than stored independently.
 *
 * <h2>Design choices</h2>
 * <p>Local state rules belong here; cross-slot overlap, patient existence, ownership and time checks
 * are performed by services and the facade.
 *
 * <h2>Important details</h2>
 * <p>The doctor is required; the patient is optional. Both associations are lazy without cascades.
 * The constructor checks doctor and start for null but does not validate duration or clock rules.
 * The version detects concurrent updates to this row, not overlaps between newly inserted rows.
 * Equality compares nonnull database identifiers after resolving proxy types; two distinct transient
 * instances are unequal. Hash codes use the effective entity class and remain stable when an identifier
 * is assigned. This trades hash distribution for stable membership in small sets.
 */
@Entity
@Table(name = "visits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "date_time", nullable = false)
    private LocalDateTime dateTime;

    @Column(name = "duration_in_minutes", nullable = false)
    private int durationInMinutes;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    /**
     * Optional patient foreign key, exposed through availability and {@code Optional}-returning helpers.
     *
     * <p>A null reference represents a free slot; related patients are not cascade-deleted.
     */
    @Getter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    /**
     * Optimistic version checked when a managed row is updated.
     *
     * <p>A conflicting update can fail at flush or commit. This version does not serialize insertion of
     * different overlapping slots; slot creation uses a separate doctor-lock protocol.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Creates a free unpersisted slot with a required doctor and start.
     *
     * @param doctor the nonnull doctor
     * @param dateTime the nonnull local clinic start
     * @param durationInMinutes the duration; service rules enforce bounds and alignment before creation
     * @throws NullPointerException if doctor or start is null
     */
    public Visit(Doctor doctor, LocalDateTime dateTime, int durationInMinutes) {
        this.doctor = Objects.requireNonNull(doctor);
        this.dateTime = Objects.requireNonNull(dateTime);
        this.durationInMinutes = durationInMinutes;
    }

    /**
     * Derives the local end from start and duration.
     *
     * @return the calculated end without an offset
     */
    public LocalDateTime endTime() {
        return dateTime.plusMinutes(durationInMinutes);
    }

    /**
     * Builds the derived half-open local interval.
     *
     * @return the interval
     * @throws IllegalArgumentException if the stored duration does not produce an increasing interval
     */
    public TimeRange timeRange() {
        return TimeRange.of(dateTime, durationInMinutes);
    }

    /**
     * Checks whether the patient association is absent.
     *
     * @return true for a free slot
     */
    public boolean isAvailable() {
        return patient == null;
    }

    /**
     * Exposes optional booking state without a nullable return container.
     *
     * @return the associated patient, or an empty Optional for a free slot
     */
    public Optional<Patient> findPatient() {
        return Optional.ofNullable(patient);
    }

    /**
     * Assigns a patient only when the slot is free.
     *
     * <p>This method changes an object; persistence requires it to be managed in a write transaction.
     * The facade/service provides that boundary, performs time and ownership checks, and lets flush check
     * the version. Calling this method on a detached entity does not save it.
     *
     * @param newPatient the nonnull patient
     * @throws DomainException if the slot is already booked
     * @throws NullPointerException if a free slot is given a null patient
     */
    public void book(Patient newPatient) {
        if (patient != null) {
            throw new DomainException(ErrorCode.VISIT_ALREADY_BOOKED, "Visit " + id + " is already booked");
        }
        this.patient = Objects.requireNonNull(newPatient);
    }

    /**
     * Clears the patient reference, including when the slot is already free.
     *
     * <p>Persistence requires managed state in a caller's write transaction. This method performs no
     * ownership check; the booking facade owns that responsibility.
     */
    public void cancelBooking() {
        this.patient = null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || EntityIdentity.effectiveClass(this) != EntityIdentity.effectiveClass(other)) {
            return false;
        }
        Long id = getId();
        return id != null && id.equals(((Visit) other).getId());
    }

    @Override
    public final int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "Visit{id=" + getId() + ", dateTime=" + dateTime + ", duration=" + durationInMinutes + "}";
    }
}
