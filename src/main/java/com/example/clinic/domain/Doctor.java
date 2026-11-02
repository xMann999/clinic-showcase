package com.example.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

/**
 * Represents a doctor profile linked to an account and shared facilities.
 *
 * <h2>Responsibility</h2>
 * <p>Stores descriptive fields and controls doctor-facility association changes.
 *
 * <h2>How it works</h2>
 * <p>The doctor owns {@code user_id} and the {@code doctor_facility} join table. Helpers synchronize
 * the inverse facility collection as well as the owning set.
 *
 * <h2>Design choices</h2>
 * <p>Visits are queried separately rather than exposed as a doctor collection. Shared accounts and
 * facilities have no persist/remove cascade from this entity.
 *
 * <h2>Important details</h2>
 * <p>The account is required and unique per doctor. Facility loading is lazy and batch size 16 can group
 * collection initialization; it is not a guarantee of one query for all doctors.
 * Equality compares nonnull database identifiers after resolving proxy types; two distinct transient
 * instances are unequal. Hash codes use the effective entity class and remain stable when an identifier
 * is assigned. This trades hash distribution for stable membership in small sets.
 */
@Entity
@Table(name = "doctors")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Setter
    @Column(name = "specialization", nullable = false, length = 100)
    private String specialization;

    /**
     * Required, lazy account association owned by this entity through {@code user_id}.
     *
     * <p>The unique foreign key allows at most one doctor per account. No cascade removes the shared account.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    /**
     * Owning side of the many-to-many facility association.
     *
     * <p>The set defines join-table membership. Domain helpers synchronise the inverse side in memory.
     * No remove cascade deletes shared facilities. Batch fetching may group lazy collection loads.
     */
    @Getter(AccessLevel.NONE)
    @ManyToMany
    @JoinTable(
        name = "doctor_facility",
        joinColumns = @JoinColumn(name = "doctor_id"),
        inverseJoinColumns = @JoinColumn(name = "facility_id")
    )
    @BatchSize(size = 16)
    private Set<Facility> facilities = new HashSet<>();

    /**
     * Creates an unpersisted doctor for a nonnull account, without facility associations.
     *
     * @param firstName the first name
     * @param lastName the surname
     * @param specialization the specialization
     * @param user the required account; existence and uniqueness are checked outside this constructor
     * @throws NullPointerException if the account is null
     */
    public Doctor(String firstName, String lastName, String specialization, User user) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.specialization = specialization;
        this.user = Objects.requireNonNull(user);
    }

    /**
     * Adds a facility and synchronises its inverse doctor set if the pair is new.
     *
     * @param facility the facility to associate
     */
    public void addFacility(Facility facility) {
        if (facilities.add(facility)) {
            facility.doctorsInternal().add(this);
        }
    }

    /**
     * Removes an existing pair from both owning and inverse collections.
     *
     * @param facility the facility to disassociate; an absent pair leaves state unchanged
     */
    public void removeFacility(Facility facility) {
        if (facilities.remove(facility)) {
            facility.doctorsInternal().remove(this);
        }
    }

    /**
     * Removes all doctor-facility pairs through the synchronising helper; facility entities remain.
     */
    public void removeAllFacilities() {
        Set.copyOf(facilities).forEach(this::removeFacility);
    }

    /**
     * Returns a live unmodifiable view of assigned facilities.
     *
     * @return the read-only view, reflecting subsequent domain changes
     */
    public Set<Facility> getFacilities() {
        return Collections.unmodifiableSet(facilities);
    }

    /**
     * Combines the stored first and last names with a space.
     *
     * @return the derived display name
     */
    public String fullName() {
        return firstName + " " + lastName;
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
        return id != null && id.equals(((Doctor) other).getId());
    }

    @Override
    public final int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "Doctor{id=" + getId() + "}";
    }
}
