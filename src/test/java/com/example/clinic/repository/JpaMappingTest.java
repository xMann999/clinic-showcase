package com.example.clinic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Mapowanie JPA na schemacie z migracji Liquibase (ten sam changelog co aplikacja).
 *
 * Każdy test zapisuje dane, wykonuje flush (SQL trafia do bazy) i clear (pusty persistence
 * context), a dopiero potem czyta. Bez clear odczyt zwróciłby te same obiekty z pamięci
 * i test nie sprawdziłby, co naprawdę zostało zapisane.
 *
 * @DataJpaTest: wycinek z JPA, repozytoriami i Liquibase; każdy test w transakcji
 * cofanej na końcu.
 */
@DataJpaTest
class JpaMappingTest {

    @Autowired
    private TestEntityManager em;

    private User user(String prefix) {
        return em.persist(new User(prefix + "@example.test", "HASH", Role.USER));
    }

    private Patient patient(User owner, String idCard) {
        Patient patient = new Patient("Ewa", "Nowak", idCard, LocalDate.of(1990, 1, 1), null);
        owner.addPatient(patient);
        return em.persist(patient);
    }

    @Test
    void oneToMany_userPatients_readBackAfterClear() {
        User owner = user("owner");
        patient(owner, "AAA000001");
        patient(owner, "AAA000002");
        em.flush();
        em.clear();

        User reloaded = em.find(User.class, owner.getId());
        assertThat(reloaded.getPatients()).hasSize(2);
        assertThat(reloaded.getPatients()).allSatisfy(p -> assertThat(p.belongsTo(owner.getId())).isTrue());
    }

    @Test
    void oneToOne_secondDoctorForSameAccount_rejectedByDatabase() {
        User account = user("doc");
        em.persistAndFlush(new Doctor("Jan", "A", "Cardiology", account));

        // Pomijamy serwis - sprawdzamy, że broni sam UNIQUE uk_doctors_user_id.
        assertThatThrownBy(() -> em.persistAndFlush(new Doctor("Jan", "B", "Cardiology", account))).isInstanceOf(
            PersistenceException.class
        );
    }

    @Test
    void manyToMany_addTwiceRemoveAndReload_noDuplicatesBothSidesConsistent() {
        Doctor doctor = em.persist(new Doctor("Jan", "Kowalski", "Cardiology", user("d1")));
        Facility north = em.persist(new Facility("North", "Street 1"));
        Facility south = em.persist(new Facility("South", "Street 2"));
        doctor.addFacility(north);
        doctor.addFacility(north);
        doctor.addFacility(south);
        assertThat(north.getDoctors()).containsExactly(doctor);
        em.flush();
        em.clear();

        Doctor reloaded = em.find(Doctor.class, doctor.getId());
        assertThat(reloaded.getFacilities()).extracting(Facility::getName).containsExactlyInAnyOrder("North", "South");
        assertThat(pairCount(doctor.getId())).isEqualTo(2);

        reloaded.removeFacility(em.find(Facility.class, north.getId()));
        em.flush();
        em.clear();
        assertThat(pairCount(doctor.getId())).isEqualTo(1);
        assertThat(em.find(Facility.class, north.getId())).isNotNull();
    }

    @Test
    void visit_nullablePatient_freeSlotThenBookingWithVersionIncrement() {
        Doctor doctor = em.persist(new Doctor("Jan", "K", "Cardiology", user("d2")));
        Visit visit = em.persistAndFlush(new Visit(doctor, LocalDateTime.of(2030, 1, 8, 10, 0), 30));
        em.clear();

        Visit free = em.find(Visit.class, visit.getId());
        assertThat(free.isAvailable()).isTrue();
        assertThat(free.getVersion()).isZero();

        free.book(patient(user("p"), "AAA000003"));
        em.flush();
        em.clear();
        Visit booked = em.find(Visit.class, visit.getId());
        assertThat(booked.isAvailable()).isFalse();
        assertThat(booked.getVersion()).isEqualTo(1);
        assertThat(booked.endTime()).isEqualTo(LocalDateTime.of(2030, 1, 8, 10, 30));
    }

    @Test
    void deleteDoctor_keepsAccountPatientsAndSharedFacilities() {
        User account = user("d3");
        Patient ownPatient = patient(account, "AAA000004");
        Facility shared = em.persist(new Facility("Shared", "Street 3"));
        Doctor other = em.persist(new Doctor("Ola", "B", "Neurology", user("d4")));
        Doctor doctor = em.persist(new Doctor("Jan", "C", "Cardiology", account));
        doctor.addFacility(shared);
        other.addFacility(shared);
        em.flush();
        em.clear();

        Doctor toDelete = em.find(Doctor.class, doctor.getId());
        toDelete.removeAllFacilities();
        em.remove(toDelete);
        em.flush();
        em.clear();

        assertThat(em.find(Doctor.class, doctor.getId())).isNull();
        assertThat(em.find(User.class, account.getId())).isNotNull();
        assertThat(em.find(Patient.class, ownPatient.getId())).isNotNull();
        assertThat(em.find(Facility.class, shared.getId()).getDoctors())
            .extracting(Doctor::getId)
            .containsExactly(other.getId());
    }

    @Test
    void equality_transientEntitiesNotEqual_hashStableAcrossPersist() {
        Facility first = new Facility("Same", "Same");
        Facility second = new Facility("Same", "Same");
        assertThat(first).isNotEqualTo(second).isEqualTo(first);

        Set<Facility> set = new HashSet<>();
        set.add(first);
        int hashBefore = first.hashCode();
        em.persistAndFlush(first);
        first.setName("Renamed");

        assertThat(first.getId()).isNotNull();
        assertThat(first.hashCode()).isEqualTo(hashBefore);
        assertThat(set).contains(first);
    }

    @Test
    void equality_proxyAndDetachedEntity_symmetricWithoutInitializingProxy() {
        Patient detached = patient(user("p2"), "AAA000005");
        em.flush();
        em.clear();

        Patient proxy = em.getEntityManager().getReference(Patient.class, detached.getId());

        assertThat(Hibernate.isInitialized(proxy)).isFalse();
        assertThat(proxy.getClass()).isNotEqualTo(Patient.class);
        assertThat(proxy).isEqualTo(detached);
        assertThat(detached).isEqualTo(proxy);
        assertThat(proxy.hashCode()).isEqualTo(detached.hashCode());
        // equals/hashCode nie wymusiły SELECT-a ani przejścia po relacjach.
        assertThat(Hibernate.isInitialized(proxy)).isFalse();
    }

    private long pairCount(Long doctorId) {
        return (
            (Number) em
                .getEntityManager()
                .createNativeQuery("select count(*) from doctor_facility where doctor_id = ?1")
                .setParameter(1, doctorId)
                .getSingleResult()
        ).longValue();
    }
}
