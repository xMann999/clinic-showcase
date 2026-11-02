package com.example.showcaselab.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import java.util.UUID;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stany encji (transient, managed, detached, removed), flush kontra commit, merge oraz strategie identyfikatorów.
 * <h2>Cel</h2>
 * Odróżnić stan encji, synchronizację SQL i zatwierdzenie transakcji oraz moment nadania ID.
 * <h2>Jak działa przykład</h2>
 * merge oraz strategie identyfikatorów.
 * JPA to specyfikacja (jakarta.persistence.*), Hibernate - jej implementacja.
 * EntityManager trzyma "persistence context": mapę encji zarządzanych, których zmiany
 * zostaną zapisane przy flush (dirty checking).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Persist, clear, merge i remove zmieniają stan encji.</li>
 * <li>Flush nie udostępnia wiersza przed zatwierdzeniem.</li>
 * <li>Strategia ID decyduje o momencie INSERT w tej konfiguracji.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Wyniki dotyczą lokalnego Hibernate i H2. Modele równości są zwykłymi obiektami; przypisanie ID nie jest operacją persist.
 */
@DisplayName(
    "Stany encji (transient, managed, detached, removed), flush kontra commit, merge oraz strategie identyfikatorów."
)
class EntityLifecycleLabTest {

    @Entity(name = "LifecycleNote")
    static class Note {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String text;

        protected Note() {}

        Note(String text) {
            this.text = text;
        }
    }

    @Entity(name = "SequenceNote")
    static class SequenceNote {

        @Id
        @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "note_seq")
        @SequenceGenerator(name = "note_seq", sequenceName = "note_seq", allocationSize = 50)
        Long id;

        String text = "s";
    }

    @Entity(name = "UuidNote")
    static class UuidNote {

        @Id
        @GeneratedValue(strategy = GenerationType.UUID)
        UUID id;

        String text = "u";
    }

    private static EntityManagerFactory factory;

    @BeforeAll
    static void start() {
        factory = LabJpa.create("lifecycle", Note.class, SequenceNote.class, UuidNote.class);
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    /**
     * Persist, clear, merge i remove zmieniają stan encji.
     */
    @Test
    @DisplayName("Persist, clear, merge i remove zmieniają stan encji")
    void shouldChangeManagedStateWhenLifecycleOperationsRun() {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            Note note = new Note("draft");
            assertThat(em.contains(note)).as("encja nowa").isFalse();

            em.persist(note);
            assertThat(em.contains(note)).as("encja zarządzana").isTrue();

            note.text = "edited";
            Statistics statistics = LabJpa.statistics(factory);
            statistics.clear();
            em.flush();
            assertThat(statistics.getEntityUpdateCount()).as("wykrycie zmiany bez save()").isEqualTo(1);

            em.clear();
            assertThat(em.contains(note)).as("encja odłączona").isFalse();
            note.text = "change of detached object";

            Note managedCopy = em.merge(note);
            assertThat(managedCopy).isNotSameAs(note);
            assertThat(em.contains(managedCopy)).isTrue();
            assertThat(em.contains(note)).isFalse();

            em.remove(managedCopy);
            assertThat(em.contains(managedCopy)).as("encja usunięta").isFalse();
            em.getTransaction().commit();
            assertThat(em.find(Note.class, note.id)).isNull();
        }
    }

    /**
     * Flush nie udostępnia wiersza przed zatwierdzeniem.
     * SQL INSERT jest już w bazie, ale niezatwierdzony - inna transakcja go nie widzi.
     */
    @Test
    @DisplayName("Flush nie udostępnia wiersza przed zatwierdzeniem")
    void shouldExposeRowWhenTransactionCommits() {
        try (
            EntityManager writer = factory.createEntityManager();
            EntityManager reader = factory.createEntityManager()
        ) {
            writer.getTransaction().begin();
            Note note = new Note("pending");
            writer.persist(note);
            writer.flush();

            assertThat(reader.find(Note.class, note.id)).isNull();
            writer.getTransaction().commit();
            reader.clear();
            assertThat(reader.find(Note.class, note.id).text).isEqualTo("pending");
        }
    }

    /**
     * Strategia ID decyduje o momencie INSERT w tej konfiguracji.
     * IDENTITY: id zna dopiero baza, więc INSERT musi pójść od razu (bez batchowania).
     * SEQUENCE (pula 50 wartości) i UUID: id nadane w pamięci, INSERT odłożony do flush.
     */
    @Test
    @DisplayName("Strategia ID decyduje o momencie INSERT w tej konfiguracji")
    void shouldDelayInsertWhenIdentifierCanBeAssignedBeforeFlush() {
        Statistics statistics = LabJpa.statistics(factory);
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();

            statistics.clear();
            em.persist(new Note("identity"));

            assertThat(statistics.getEntityInsertCount()).isEqualTo(1);

            statistics.clear();
            SequenceNote sequence = new SequenceNote();
            em.persist(sequence);
            UuidNote uuid = new UuidNote();
            em.persist(uuid);

            assertThat(sequence.id).isNotNull();
            assertThat(uuid.id).isNotNull();
            assertThat(statistics.getEntityInsertCount()).isZero();

            em.flush();
            assertThat(statistics.getEntityInsertCount()).isEqualTo(2);
            em.getTransaction().rollback();
        }
    }
}
