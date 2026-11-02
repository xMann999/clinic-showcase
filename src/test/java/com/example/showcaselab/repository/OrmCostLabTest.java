package com.example.showcaselab.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.ClinicShowcaseApplication;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import jakarta.persistence.EntityGraph;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ContextConfiguration;

/**
 * Koszt relacji LAZY - problem N+1 i trzy sposoby jego uniknięcia.
 * <h2>Cel</h2>
 * Porównać koszt odczytu relacji przy pustym kontekście trwałości, nie zmieniając zawartości wyników.
 * <h2>Jak działa przykład</h2>
 * Fixture: trzech RÓŻNYCH doktorów, każdy z jedną wizytą i dwiema placówkami (powtarzanie
 * jednego doktora ukryłoby problem, bo kontekst trwałości ładuje go tylko raz).
 * Każdy pomiar zaczyna się od pustego kontekstu (clear) i wyzerowanych statystyk Hibernate.
 * Fetch join kolekcji NIE nadaje się do paginacji (Hibernate stronicowałby w pamięci) -
 * dlatego lista doktorów w API nie zwraca placówek, a szczegół używa @EntityGraph.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Odczyt relacji LAZY powoduje dodatkowe zapytanie dla każdej wizyty.</li>
 * <li>Fetch join pobiera wizyty i lekarzy jednym zapytaniem.</li>
 * <li>EntityGraph pobiera lekarzy bez zmiany JPQL.</li>
 * <li>BatchSize pobiera kolekcje jednym dodatkowym zapytaniem.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Testy używają H2 i rzeczywistych encji aplikacji. Liczby zapytań dotyczą tej konfiguracji i przygotowanych danych.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(classes = ClinicShowcaseApplication.class)
@DisplayName("Koszt relacji LAZY - problem N+1 i trzy sposoby jego uniknięcia.")
class OrmCostLabTest {

    @Autowired
    private TestEntityManager em;

    private final List<Long> doctorIds = new ArrayList<>();
    private Statistics statistics;

    @BeforeEach
    void fixture() {
        for (int index = 0; index < 3; index++) {
            Doctor doctor = em.persist(
                new Doctor(
                    "Doc",
                    "No" + index,
                    "Spec",
                    em.persist(new User("orm" + index + "@lab.test", "H", Role.USER))
                )
            );
            doctor.addFacility(em.persist(new Facility("F" + index + "a", "Street")));
            doctor.addFacility(em.persist(new Facility("F" + index + "b", "Street")));
            em.persist(new Visit(doctor, LocalDateTime.of(2030, 1, 8, 9 + index, 0), 30));
            doctorIds.add(doctor.getId());
        }
        em.flush();
        em.clear();
        statistics = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    /**
     * Odczyt relacji LAZY powoduje dodatkowe zapytanie dla każdej wizyty.
     */
    @Test
    @DisplayName("Odczyt relacji LAZY powoduje dodatkowe zapytanie dla każdej wizyty")
    void shouldIssueExtraQueriesWhenLazyRelationsAreRead() {
        List<Visit> visits = em
            .getEntityManager()
            .createQuery("select v from Visit v where v.doctor.id in :ids order by v.id", Visit.class)
            .setParameter("ids", doctorIds)
            .getResultList();
        assertThat(visits).hasSize(3);
        for (Visit visit : visits) {
            assertThat(visit.getDoctor().getLastName()).startsWith("No");
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + 3);
    }

    /**
     * Fetch join pobiera wizyty i lekarzy jednym zapytaniem.
     */
    @Test
    @DisplayName("Fetch join pobiera wizyty i lekarzy jednym zapytaniem")
    void shouldUseOneQueryWhenFetchJoinLoadsDoctors() {
        List<Visit> visits = em
            .getEntityManager()
            .createQuery(
                "select v from Visit v join fetch v.doctor where v.doctor.id in :ids order by v.id",
                Visit.class
            )
            .setParameter("ids", doctorIds)
            .getResultList();
        assertThat(visits).hasSize(3);
        for (Visit visit : visits) {
            assertThat(visit.getDoctor().getLastName()).startsWith("No");
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    /**
     * EntityGraph pobiera lekarzy bez zmiany JPQL.
     */
    @Test
    @DisplayName("EntityGraph pobiera lekarzy bez zmiany JPQL")
    void shouldUseOneQueryWhenEntityGraphLoadsDoctors() {
        EntityGraph<Visit> graph = em.getEntityManager().createEntityGraph(Visit.class);
        graph.addAttributeNodes("doctor");
        List<Visit> visits = em
            .getEntityManager()
            .createQuery("select v from Visit v where v.doctor.id in :ids order by v.id", Visit.class)
            .setParameter("ids", doctorIds)
            .setHint("jakarta.persistence.fetchgraph", graph)
            .getResultList();
        assertThat(visits).hasSize(3);
        for (Visit visit : visits) {
            assertThat(visit.getDoctor().getLastName()).startsWith("No");
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    /**
     * BatchSize pobiera kolekcje jednym dodatkowym zapytaniem.
     * {@code @BatchSize}(16) na Doctor.facilities: 1 zapytanie o doktorów + 1 o placówki wszystkich trzech.
     * Bez @BatchSize byłoby 1 + 3.
     */
    @Test
    @DisplayName("BatchSize pobiera kolekcje jednym dodatkowym zapytaniem")
    void shouldBatchCollectionsWhenBatchSizeIsConfigured() {
        List<Doctor> doctors = em
            .getEntityManager()
            .createQuery("select d from Doctor d where d.id in :ids order by d.id", Doctor.class)
            .setParameter("ids", doctorIds)
            .getResultList();
        assertThat(doctors).hasSize(3);
        for (Doctor doctor : doctors) {
            assertThat(doctor.getFacilities()).hasSize(2);
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }
}
