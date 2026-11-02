package com.example.clinic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Zapytania repozytoriów na znanym fixture: derived queries, JPQL, projekcje, operacja masowa,
 * Specification i stabilna paginacja.
 */
@DataJpaTest
class RepositoryQueriesTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2030, 1, 8, 0, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private PatientRepository patients;

    @Autowired
    private VisitRepository visits;

    private User owner;
    private Doctor doctorA;
    private Doctor doctorB;
    private Patient anna;
    private Patient piotr;

    @BeforeEach
    void fixture() {
        owner = em.persist(new User("family@example.test", "HASH", Role.USER));
        User other = em.persist(new User("other@example.test", "HASH", Role.USER));
        anna = new Patient("Anna", "Kowalska", "QRY000001", LocalDate.of(1985, 3, 1), "+48500000001");
        piotr = new Patient("Piotr", "Kowalski", "QRY000002", LocalDate.of(2010, 6, 1), "+48500000002");
        Patient zofia = new Patient("Zofia", "Nowak", "QRY000003", LocalDate.of(1970, 1, 1), null);
        owner.addPatient(anna);
        owner.addPatient(piotr);
        other.addPatient(zofia);
        em.persist(anna);
        em.persist(piotr);
        em.persist(zofia);
        doctorA = em.persist(
            new Doctor("Jan", "A", "Cardiology", em.persist(new User("a@example.test", "H", Role.USER)))
        );
        doctorB = em.persist(
            new Doctor("Ola", "B", "Neurology", em.persist(new User("b@example.test", "H", Role.USER)))
        );
        em.flush();
    }

    @Test
    void derivedQuery_throughRelation_findsPatientsByAccountEmail() {
        Page<Patient> page = patients.findAllByUserEmail("family@example.test", PageRequest.of(0, 10, Sort.by("id")));

        assertThat(page.getContent()).extracting(Patient::getFirstName).containsExactly("Anna", "Piotr");
    }

    @Test
    void derivedQuery_operators_betweenBeforeContainingOrderBy() {
        assertThat(
            patients.findAllByBirthdayBetweenOrderByLastNameAsc(LocalDate.of(1980, 1, 1), LocalDate.of(2015, 1, 1))
        )
            .extracting(Patient::getLastName)
            .containsExactly("Kowalska", "Kowalski");
        assertThat(patients.findAllByBirthdayBeforeAndLastNameContainingIgnoreCase(LocalDate.of(2000, 1, 1), "kowal"))
            .extracting(Patient::getFirstName)
            .containsExactly("Anna");
    }

    @Test
    void projections_interfaceAndConstructor_returnOnlySelectedColumns() {
        List<PatientNameView> views = patients.findAllProjectedByUserIdOrderByIdAsc(owner.getId());
        List<PatientNameRow> rows = patients.findNameRowsByUserId(owner.getId());

        assertThat(views).extracting(PatientNameView::getLastName).containsExactly("Kowalska", "Kowalski");
        assertThat(rows).containsExactly(
            new PatientNameRow(anna.getId(), "Kowalska"),
            new PatientNameRow(piotr.getId(), "Kowalski")
        );
    }

    @Test
    void modifyingBulkUpdate_clearsContextSoReloadSeesDatabaseState() {
        Patient managed = patients.findById(anna.getId()).orElseThrow();
        assertThat(managed.getPhoneNumber()).isNotNull();

        int updated = patients.clearPhoneNumbersOfUser(owner.getId());

        assertThat(updated).isEqualTo(2);
        // clearAutomatically: kontekst wyczyszczony, więc findById czyta nowy stan z bazy.
        assertThat(patients.findById(anna.getId()).orElseThrow().getPhoneNumber()).isNull();
        assertThat(patients.countWithoutPhoneNative()).isEqualTo(3);
    }

    @Test
    void doctorDayQuery_returnsOnlyThatDoctorAndDay() {
        visit(doctorA, DAY.withHour(9), null);
        visit(doctorA, DAY.withHour(23).withMinute(45), null);
        visit(doctorA, DAY.plusDays(1), null);
        visit(doctorB, DAY.withHour(9), null);

        assertThat(visits.findDoctorVisitsBetween(doctorA.getId(), DAY, DAY.plusDays(1)))
            .extracting(Visit::getDateTime)
            .containsExactly(DAY.withHour(9), DAY.withHour(23).withMinute(45));
    }

    @Test
    void specification_eachFilterAloneAndCombined_missingFilterDoesNotRemoveRows() {
        visit(doctorA, DAY.withHour(9), anna);
        visit(doctorA, DAY.withHour(10), null);
        visit(doctorB, DAY.withHour(9), null);
        visit(doctorB, DAY.plusDays(1).withHour(9), piotr);

        assertThat(count(new VisitSearchCriteria(null, null, null, null, null))).isEqualTo(4);
        assertThat(count(new VisitSearchCriteria(doctorA.getId(), null, null, null, null))).isEqualTo(2);
        assertThat(count(new VisitSearchCriteria(null, anna.getId(), null, null, null))).isEqualTo(1);
        assertThat(count(new VisitSearchCriteria(null, null, DAY.withHour(10), null, null))).isEqualTo(2);
        assertThat(count(new VisitSearchCriteria(null, null, null, DAY.withHour(10), null))).isEqualTo(2);
        assertThat(count(new VisitSearchCriteria(null, null, null, null, true))).isEqualTo(2);
        assertThat(count(new VisitSearchCriteria(null, null, null, null, false))).isEqualTo(2);
        assertThat(count(new VisitSearchCriteria(doctorB.getId(), null, DAY, DAY.plusDays(1), true))).isEqualTo(1);
    }

    @Test
    void leftJoinFetch_freeSlotIsNotLostBecauseOfNullPatient() {
        Visit free = visit(doctorA, DAY.withHour(11), null);
        em.clear();

        assertThat(visits.findWithDoctorAndPatientById(free.getId()))
            .isPresent()
            .get()
            .satisfies(v -> assertThat(v.isAvailable()).isTrue());
    }

    @Test
    void paging_sameDateTimeWithIdTieBreaker_noRecordLostOrDuplicated() {
        Visit v1 = visit(doctorA, DAY.withHour(12), null);
        Visit v2 = visit(doctorB, DAY.withHour(12), null);
        Visit v3 = visit(doctorA, DAY.withHour(13), null);
        Sort sort = Sort.by("dateTime").and(Sort.by("id"));

        Page<Visit> first = visits.findAll(PageRequest.of(0, 2, sort));
        Page<Visit> second = visits.findAll(PageRequest.of(1, 2, sort));

        assertThat(first.getContent()).extracting(Visit::getId).containsExactly(v1.getId(), v2.getId());
        assertThat(second.getContent()).extracting(Visit::getId).containsExactly(v3.getId());
        assertThat(second.isLast()).isTrue();
        assertThat(first.getTotalElements()).isEqualTo(3);

        Page<Visit> descending = visits.findAll(
            PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "dateTime").and(Sort.by("id")))
        );
        assertThat(descending.getContent())
            .extracting(Visit::getId)
            .containsExactly(v3.getId(), v1.getId(), v2.getId());
        assertThat(visits.findAll(PageRequest.of(5, 2, sort)).getContent()).isEmpty();
    }

    private long count(VisitSearchCriteria criteria) {
        return visits.count(VisitSpecifications.matching(criteria));
    }

    private Visit visit(Doctor doctor, LocalDateTime start, Patient patient) {
        Visit visit = new Visit(doctor, start, 30);
        if (patient != null) {
            visit.book(patient);
        }
        return em.persistAndFlush(visit);
    }
}
