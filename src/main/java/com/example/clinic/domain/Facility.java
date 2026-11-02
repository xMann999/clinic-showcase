package com.example.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a clinic facility that can exist without doctors.
 *
 * <h2>Responsibility</h2>
 * <p>Stores name and address and exposes assigned doctors as a read-only view.
 *
 * <h2>How it works</h2>
 * <p>The doctor collection is the inverse side, with {@code mappedBy = "facilities"}; the doctor's
 * owning set determines join-table writes.
 *
 * <h2>Design choices</h2>
 * <p>Doctor helpers synchronize both sides. Facility deletion checks for remaining assignments in the service.
 *
 * <h2>Important details</h2>
 * <p>The collection is lazy and has no persist/remove cascade or orphan removal.
 * Equality compares nonnull database identifiers after resolving proxy types; two distinct transient
 * instances are unequal. Hash codes use the effective entity class and remain stable when an identifier
 * is assigned. This trades hash distribution for stable membership in small sets.
 */
@Entity
@Table(name = "facilities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Facility {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Setter
    @Column(name = "address", nullable = false, length = 300)
    private String address;

    /**
     * Inverse doctor association maintained by the doctor's relationship helpers.
     *
     * <p>Changes to this collection alone do not define join-table writes. The service checks associations
     * before deletion; there is no cascade that deletes doctors.
     */
    @Getter(AccessLevel.NONE)
    @ManyToMany(mappedBy = "facilities")
    private Set<Doctor> doctors = new HashSet<>();

    public Facility(String name, String address) {
        this.name = name;
        this.address = address;
    }

    /**
     * Exposes the mutable inverse set to package collaborators that synchronize associations.
     *
     * @return the live internal set; callers must preserve both relationship sides
     */
    Set<Doctor> doctorsInternal() {
        return doctors;
    }

    /**
     * Returns a live unmodifiable view of the inverse doctor set.
     *
     * @return the read-only view; relationship mutations must use doctor helpers
     */
    public Set<Doctor> getDoctors() {
        return Collections.unmodifiableSet(doctors);
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
        return id != null && id.equals(((Facility) other).getId());
    }

    @Override
    public final int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "Facility{id=" + getId() + "}";
    }
}
