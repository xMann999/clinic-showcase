package com.example.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents an account holding normalized email, password hash and role.
 *
 * <h2>Responsibility</h2>
 * <p>Controls account mutations and synchronizes patient ownership.
 *
 * <h2>How it works</h2>
 * <p>JPA uses field access because the identifier annotation is on the field. Lombok supplies getters
 * and the protected constructor used for entity instantiation; application code uses the data constructor.
 *
 * <h2>Design choices</h2>
 * <p>Explicit equality and string methods avoid including credentials or traversing all relationships.
 * Patients remain independent resources; deleting an account does not cascade into them.
 *
 * <h2>Important details</h2>
 * <p>The inverse patient list has no cascade or orphan removal. Its read-only wrapper is a live view,
 * not an immutable snapshot. {@code addPatient} may append duplicates to this list.
 * Equality compares nonnull database identifiers after resolving proxy types; two distinct transient
 * instances are unequal. Hash codes use the effective entity class and remain stable when an identifier
 * is assigned. This trades hash distribution for stable membership in small sets.
 */
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    /**
     * Database-generated identity assigned on insertion; it is absent on a new transient account.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /**
     * Role stored by enum name rather than ordinal position, so reordering constants does not change stored meaning.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /**
     * Inverse patient list mapped by {@code Patient.user}.
     *
     * <p>The patient foreign key determines persistence. No cascade or orphan removal deletes patients
     * when this list changes or the account is removed.
     */
    @Getter(AccessLevel.NONE)
    @OneToMany(mappedBy = "user")
    private List<Patient> patients = new ArrayList<>();

    /**
     * Creates an unpersisted account, normalizing email and retaining the supplied hash and role.
     *
     * @param email the nonnull email to normalize
     * @param passwordHash the already encoded password; this constructor does not hash plaintext
     * @param role the account role, validated by the input/persistence boundary
     */
    public User(String email, String passwordHash, Role role) {
        this.email = EmailAddress.normalize(email);
        this.passwordHash = passwordHash;
        this.role = role;
    }

    /**
     * Replaces the email using the shared normalization policy.
     *
     * @param newEmail the nonnull email; validity and uniqueness are checked outside the entity
     */
    public void changeEmail(String newEmail) {
        this.email = EmailAddress.normalize(newEmail);
    }

    /**
     * Replaces the stored role without performing authorization.
     *
     * @param newRole the role to store; the caller must satisfy persistence/input constraints
     */
    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    /**
     * Replaces the stored hash; this operation does not encode plaintext.
     *
     * @param newPasswordHash the already encoded password
     */
    public void changePasswordHash(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
    }

    /**
     * Assigns the patient's owner and appends it to this account's inverse list.
     *
     * <p>The owning {@code Patient.user} field determines the database relationship. This list makes the
     * association visible in memory but does not persist a new patient automatically or deduplicate entries.
     *
     * @param patient the patient to associate
     * @throws IllegalStateException if the patient belongs to a different account
     */
    public void addPatient(Patient patient) {
        patient.assignOwner(this);
        patients.add(patient);
    }

    /**
     * Returns a live unmodifiable view of the patient list.
     *
     * @return the read-only view; later domain changes remain visible
     */
    public List<Patient> getPatients() {
        return Collections.unmodifiableList(patients);
    }

    /**
     * Compares persisted identity using the effective entity type and identifier getter.
     *
     * <p>Only the same reference compares equal before an identifier is assigned. The final method keeps
     * proxy subclasses from replacing this identity contract.
     *
     * @param other the object to compare, possibly null
     * @return whether both objects represent the same persisted account
     */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || EntityIdentity.effectiveClass(this) != EntityIdentity.effectiveClass(other)) {
            return false;
        }
        Long id = getId();
        return id != null && id.equals(((User) other).getId());
    }

    /**
     * Returns a hash based on the effective entity class.
     *
     * <p>The value does not change on insertion or field edits, preserving set membership at the cost of
     * placing accounts of this type in the same hash bucket.
     *
     * @return the stable type hash
     */
    @Override
    public final int hashCode() {
        return EntityIdentity.effectiveClass(this).hashCode();
    }

    @Override
    public String toString() {
        return "User{id=" + getId() + ", role=" + role + "}";
    }
}
