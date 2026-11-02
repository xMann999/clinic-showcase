package com.example.showcaselab.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.clinic.ClinicShowcaseApplication;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.repository.PatientRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.repository.Repository;
import org.springframework.test.context.ContextConfiguration;

/**
 * Co kryje się za interfejsem repozytorium Spring Data.
 * <h2>Cel</h2>
 * Rozpoznać proxy repozytorium, semantykę persist i merge oraz walidację nazw zapytań pochodnych.
 * <h2>Jak działa przykład</h2>
 * - interfejs nie ma implementacji w kodzie: Spring Data tworzy proxy delegujące do
 *   SimpleJpaRepository (CRUD) i do zapytań wygenerowanych z nazw metod;
 * - save(): dla nowej encji persist (ta sama instancja staje się zarządzana),
 *   dla encji z id - merge (zwraca INNĄ, zarządzaną kopię);
 * - błędna nazwa właściwości w zapytaniu pochodnym wychodzi przy tworzeniu repozytorium,
 *   czyli przy starcie aplikacji - nie dopiero przy wywołaniu.
 * CrudRepository/ListCrudRepository/PagingAndSortingRepository to węższe "sąsiednie"
 * interfejsy; JpaRepository dokłada m.in. flush i operacje wsadowe.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Repozytorium jest proxy delegującym do implementacji JPA.</li>
 * <li>Save zapisuje nowy obiekt i scala odłączony do kopii.</li>
 * <li>Błędna właściwość zatrzymuje tworzenie repozytorium.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Testy używają H2 i rzeczywistych encji aplikacji. Liczby zapytań dotyczą tej konfiguracji i przygotowanych danych.
 */
@DataJpaTest
@ContextConfiguration(classes = ClinicShowcaseApplication.class)
@DisplayName("Co kryje się za interfejsem repozytorium Spring Data.")
class RepositoryMechanicsLabTest {

    interface BrokenPatientRepository extends Repository<Patient, Long> {
        /** Patient nie ma pola "surname" - poprawnie byłoby findAllByLastName. */
        List<Patient> findAllBySurname(String surname);
    }

    @Autowired
    private PatientRepository patients;

    @Autowired
    private TestEntityManager em;

    private Patient newPatient(String idCard) {
        User owner = em.persist(new User(idCard.toLowerCase(java.util.Locale.ROOT) + "@lab.test", "H", Role.USER));
        Patient patient = new Patient("Ala", "Lab", idCard, LocalDate.of(1990, 1, 1), null);
        owner.addPatient(patient);
        return patient;
    }

    /**
     * Repozytorium jest proxy delegującym do implementacji JPA.
     */
    @Test
    @DisplayName("Repozytorium jest proxy delegującym do implementacji JPA")
    void shouldCreateProxyWhenRepositoryIsInjected() {
        assertThat(AopUtils.isAopProxy(patients)).isTrue();
        assertThat(AopUtils.getTargetClass(patients)).isEqualTo(SimpleJpaRepository.class);
    }

    /**
     * Save zapisuje nowy obiekt i scala odłączony do kopii.
     */
    @Test
    @DisplayName("Save zapisuje nowy obiekt i scala odłączony do kopii")
    void shouldMergeIntoManagedCopyWhenEntityIsDetached() {
        Patient fresh = newPatient("REP000001");
        Patient persisted = patients.save(fresh);
        assertThat(persisted).isSameAs(fresh);
        em.flush();
        em.clear();

        fresh.setLastName("Changed");
        Patient merged = patients.save(fresh);

        assertThat(merged).isNotSameAs(fresh).isEqualTo(fresh);
        assertThat(em.getEntityManager().contains(merged)).isTrue();
        assertThat(em.getEntityManager().contains(fresh)).isFalse();
        assertThat(merged.getLastName()).isEqualTo("Changed");
        em.flush();
        em.clear();
        assertThat(em.find(Patient.class, fresh.getId()).getLastName()).isEqualTo("Changed");
    }

    /**
     * Błędna właściwość zatrzymuje tworzenie repozytorium.
     */
    @Test
    @DisplayName("Błędna właściwość zatrzymuje tworzenie repozytorium")
    void shouldRejectRepositoryWhenDerivedPropertyIsUnknown() {
        Throwable failure = catchThrowable(() ->
            new JpaRepositoryFactory(em.getEntityManager()).getRepository(BrokenPatientRepository.class)
        );

        assertThat(failure).isNotNull();
        assertThat(causeChainContains(failure, PropertyReferenceException.class)).isTrue();
    }

    private static boolean causeChainContains(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }
}
