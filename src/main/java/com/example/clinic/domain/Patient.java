package com.example.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a patient belonging to one account.
 *
 * <h2>Responsibility</h2>
 * <p>Stores personal data and controls assignment of the required owner.
 *
 * <h2>How it works</h2>
 * <p>The patient owns {@code user_id}; {@link User#addPatient(Patient)} assigns that owner and updates
 * the inverse list. Reassignment to a different owner is rejected.
 *
 * <h2>Design choices</h2>
 * <p>Only editable patient fields have Lombok setters. The API mapper excludes the identity document;
 * the custom string representation includes only the identifier.
 *
 * <h2>Important details</h2>
 * <p>The account association is lazy and has no cascade. Constructing a patient does not yet assign
 * its required owner; the service must do so before persistence.
 * Equality compares nonnull database identifiers after resolving proxy types; two distinct transient
 * instances are unequal. Hash codes use the effective entity class and remain stable when an identifier
 * is assigned. This trades hash distribution for stable membership in small sets.
 */
@Entity
@Table(name = "patients")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Setter
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    /**
     * Identity document stored under a database uniqueness constraint and excluded from API DTOs.
     */
    @Setter
    @Column(name = "id_card_no", nullable = false, length = 20)
    private String idCardNo;

    @Setter
    @Column(name = "birthday", nullable = false)
    private LocalDate birthday;

    @Setter
    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    /**
     * Required owning account association, persisted through {@code user_id}.
     *
     * <p>Lazy fetching defers account state loading where supported; no cascade creates or deletes an account.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Creates unpersisted patient data; the service must assign the required owner before saving.
     *
     * @param firstName the first name
     * @param lastName the surname
     * @param idCardNo the document number, whose validity and uniqueness are checked outside this constructor
     * @param birthday the birth date
     * @param phoneNumber the phone, or null for absence
     */
    public Patient(String firstName, String lastName, String idCardNo, LocalDate birthday, String phoneNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.idCardNo = idCardNo;
        this.birthday = birthday;
        this.phoneNumber = phoneNumber;
    }

    /**
     * Assigns an owner while preventing reassignment to a different account.
     *
     * <p>The calling user helper maintains the inverse list; this operation alone does not do so.
     *
     * @param owner the nonnull owning account
     * @throws IllegalStateException if a different owner is already assigned
     * @throws NullPointerException if the supplied owner is null
     */
    void assignOwner(User owner) {
        if (this.user != null && !this.user.equals(owner)) {
            throw new IllegalStateException("Patient owner cannot be changed");
        }
        this.user = Objects.requireNonNull(owner);
    }

    /**
     * Combines the stored first and last names with a space.
     *
     * @return the derived display name
     */
    public String fullName() {
        return firstName + " " + lastName;
    }

    /**
     * Checks the stored owner's identifier for data consistency.
     *
     * <p>This comparison does not authenticate the caller. It only needs the account identifier rather
     * than account details.
     *
     * @param userId the account identifier to compare
     * @return whether an owner exists with that identifier
     */
    public boolean belongsTo(Long userId) {
        return user != null && user.getId().equals(userId);
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
        return id != null && id.equals(((Patient) other).getId());
    }

    @Override
    public final int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }

    /**
     * Returns an identifier-only representation that excludes the document, phone and relationships.
     *
     * @return the diagnostic representation
     */
    @Override
    public String toString() {
        return "Patient{id=" + getId() + "}";
    }
}
