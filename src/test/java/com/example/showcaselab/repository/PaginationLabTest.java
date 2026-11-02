package com.example.showcaselab.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.ClinicShowcaseApplication;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.repository.VisitSpecifications;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ContextConfiguration;

/**
 * Page kontra Slice i paginacja keyset (kursor dateTime + id).
 * <h2>Cel</h2>
 * Porównać koszt Page i Slice oraz sprawdzić pełne przejście kursorem przy remisach daty.
 * <h2>Jak działa przykład</h2>
 * - Page: zawartość + liczba wszystkich elementów (zwykle dodatkowe COUNT);
 * - Slice: tylko "czy jest następna strona" - pobiera size+1 wierszy, bez COUNT;
 * - keyset: zamiast OFFSET pamiętamy ostatni (dateTime, id) i pytamy o "następne po nim".
 *   Remis dateTime rozstrzyga id, więc żaden rekord nie zginie ani się nie powtórzy.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Pełna strona wymaga COUNT, ostatnia niepełna może go pominąć.</li>
 * <li>Slice pobiera nadmiarowy wiersz bez COUNT.</li>
 * <li>Kursor czasu i ID przechodzi wszystkie remisy bez powtórzeń.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Testy używają H2 i rzeczywistych encji aplikacji. Liczby zapytań dotyczą tej konfiguracji i przygotowanych danych.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ContextConfiguration(classes = ClinicShowcaseApplication.class)
@DisplayName("Page kontra Slice i paginacja keyset (kursor dateTime + id).")
class PaginationLabTest {

    private static final LocalDateTime T = LocalDateTime.of(2030, 1, 8, 10, 0);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private VisitRepository visits;

    private Doctor doctor;
    private Statistics statistics;
    private final List<Long> expectedOrder = new ArrayList<>();

    /** Dwie pary wizyt mają ten sam czas; ID rozstrzyga remis przy przechodzeniu stron. */
    @BeforeEach
    void fixture() {
        doctor = em.persist(new Doctor("D", "Page", "S", em.persist(new User("page@lab.test", "H", Role.USER))));
        for (LocalDateTime start : List.of(T, T, T.plusHours(1), T.plusHours(1), T.plusHours(2))) {
            expectedOrder.add(em.persist(new Visit(doctor, start, 15)).getId());
        }
        em.flush();
        em.clear();
        statistics = em.getEntityManager().getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    private static final Sort BY_TIME_THEN_ID = Sort.by("dateTime").and(Sort.by("id"));

    /**
     * Pełna strona wymaga COUNT, ostatnia niepełna może go pominąć.
     * Ostatnia, niepełna strona: Spring Data wylicza total bez COUNT. Nie zakładamy "zawsze 2 SQL".
     */
    @Test
    @DisplayName("Pełna strona wymaga COUNT, ostatnia niepełna może go pominąć")
    void shouldSkipCountWhenLastPageIsPartial() {
        Page<Visit> first = visits.findAll(
            VisitSpecifications.forDoctor(doctor.getId()),
            PageRequest.of(0, 2, BY_TIME_THEN_ID)
        );
        long afterFirst = statistics.getPrepareStatementCount();
        statistics.clear();
        Page<Visit> last = visits.findAll(
            VisitSpecifications.forDoctor(doctor.getId()),
            PageRequest.of(2, 2, BY_TIME_THEN_ID)
        );

        assertThat(first.getTotalElements()).isEqualTo(5);
        assertThat(first.getTotalPages()).isEqualTo(3);
        assertThat(afterFirst).isEqualTo(2);

        assertThat(last.getContent()).hasSize(1);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    /**
     * Slice pobiera nadmiarowy wiersz bez COUNT.
     */
    @Test
    @DisplayName("Slice pobiera nadmiarowy wiersz bez COUNT")
    void shouldFetchExtraRowWithoutCountWhenUsingSlice() {
        Slice<Visit> slice = sliceOf(PageRequest.of(0, 2, BY_TIME_THEN_ID));

        assertThat(slice.getContent()).hasSize(2);
        assertThat(slice.hasNext()).isTrue();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    private Slice<Visit> sliceOf(Pageable pageable) {
        List<Visit> rows = em
            .getEntityManager()
            .createQuery("select v from Visit v where v.doctor.id = :doctor order by v.dateTime, v.id", Visit.class)
            .setParameter("doctor", doctor.getId())
            .setFirstResult((int) pageable.getOffset())
            .setMaxResults(pageable.getPageSize() + 1)
            .getResultList();
        boolean hasNext = rows.size() > pageable.getPageSize();
        return new SliceImpl<>(hasNext ? rows.subList(0, pageable.getPageSize()) : rows, pageable, hasNext);
    }

    /**
     * Kursor czasu i ID przechodzi wszystkie remisy bez powtórzeń.
     */
    @Test
    @DisplayName("Kursor czasu i ID przechodzi wszystkie remisy bez powtórzeń")
    void shouldVisitEveryRowWhenCursorIncludesTimeAndId() {
        List<Long> seen = new ArrayList<>();
        LocalDateTime lastTime = LocalDateTime.MIN;
        long lastId = Long.MIN_VALUE;
        for (int pageNumber = 0; pageNumber <= expectedOrder.size(); pageNumber++) {
            List<Visit> page = em
                .getEntityManager()
                .createQuery(
                    """
                    select v from Visit v
                    where v.doctor.id = :doctor
                      and (v.dateTime > :lastTime or (v.dateTime = :lastTime and v.id > :lastId))
                    order by v.dateTime, v.id""",
                    Visit.class
                )
                .setParameter("doctor", doctor.getId())
                .setParameter("lastTime", lastTime.equals(LocalDateTime.MIN) ? T.minusYears(1) : lastTime)
                .setParameter("lastId", lastId)
                .setMaxResults(2)
                .getResultList();
            if (page.isEmpty()) {
                break;
            }
            page.forEach(visit -> seen.add(visit.getId()));
            Visit lastOnPage = page.getLast();
            lastTime = lastOnPage.getDateTime();
            lastId = lastOnPage.getId();
        }

        assertThat(seen).containsExactlyElementsOf(expectedOrder);
    }
}
