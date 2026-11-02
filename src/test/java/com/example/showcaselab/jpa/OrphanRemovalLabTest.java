package com.example.showcaselab.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.showcaselab.jpa.CascadeModel.Folder;
import com.example.showcaselab.jpa.CascadeModel.Note;
import com.example.showcaselab.jpa.CascadeModel.RemoveOnlyFolder;
import com.example.showcaselab.jpa.CascadeModel.RemoveOnlyNote;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * OrphanRemoval kontra cascade REMOVE.
 * <h2>Cel</h2>
 * Odróżnić usunięcie rodzica od odpięcia dziecka i potwierdzić rezultat niezależnym odczytem bazy.
 * <h2>Jak działa przykład</h2>
 * - cascade REMOVE: usunięcie RODZICA usuwa dzieci;
 * - orphanRemoval: usunięcie dziecka z KOLEKCJI rodzica (odpięcie) usuwa dziecko z bazy.
 * orphanRemoval ma sens tylko dla prywatnego dziecka. Pacjent nie jest prywatnym dzieckiem
 * konta (ma wizyty, własny cykl życia), więc w aplikacji User -> patients nie ma ani jednego, ani drugiego.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Odpięcie sieroty usuwa tylko wskazane dziecko.</li>
 * <li>Bez orphanRemoval odpięte dziecko pozostaje w bazie.</li>
 * <li>Cascade REMOVE usuwa dzieci wraz z rodzicem.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Wyniki dotyczą lokalnego Hibernate i H2. Modele równości są zwykłymi obiektami; przypisanie ID nie jest operacją persist.
 */
@DisplayName("OrphanRemoval kontra cascade REMOVE.")
class OrphanRemovalLabTest {

    private static EntityManagerFactory factory;

    @BeforeAll
    static void start() {
        factory = LabJpa.create("orphans", CascadeModel.entities());
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    /**
     * Odpięcie sieroty usuwa tylko wskazane dziecko.
     */
    @Test
    @DisplayName("Odpięcie sieroty usuwa tylko wskazane dziecko")
    void shouldDeleteOnlyOrphanWhenChildIsDetached() {
        Long[] ids = LabJpa.inTransaction(factory, em -> {
            Folder folder = new Folder("f");
            Note kept = folder.add("kept");
            Note orphan = folder.add("orphan");
            em.persist(folder);
            em.flush();
            return new Long[] { folder.id, kept.id, orphan.id };
        });

        LabJpa.runInTransaction(factory, em -> {
            Folder folder = em.find(Folder.class, ids[0]);
            folder.remove(em.find(Note.class, ids[2]));
        });

        LabJpa.runInTransaction(factory, em -> {
            assertThat(em.find(Note.class, ids[2])).isNull();
            assertThat(em.find(Note.class, ids[1])).isNotNull();
            assertThat(em.find(Folder.class, ids[0])).isNotNull();
        });
    }

    /**
     * Bez orphanRemoval odpięte dziecko pozostaje w bazie.
     */
    @Test
    @DisplayName("Bez orphanRemoval odpięte dziecko pozostaje w bazie")
    void shouldKeepDetachedChildWhenOrphanRemovalIsAbsent() {
        Long[] ids = LabJpa.inTransaction(factory, em -> {
            RemoveOnlyFolder folder = new RemoveOnlyFolder();
            RemoveOnlyNote note = folder.add("child");
            em.persist(folder);
            em.flush();
            return new Long[] { folder.id, note.id };
        });

        LabJpa.runInTransaction(factory, em -> {
            RemoveOnlyFolder folder = em.find(RemoveOnlyFolder.class, ids[0]);
            RemoveOnlyNote note = em.find(RemoveOnlyNote.class, ids[1]);
            folder.notes.remove(note);
            note.folder = null;
        });

        LabJpa.runInTransaction(factory, em -> {
            RemoveOnlyNote survivor = em.find(RemoveOnlyNote.class, ids[1]);
            assertThat(survivor).isNotNull();
            assertThat(survivor.folder).isNull();
        });
    }

    /**
     * Cascade REMOVE usuwa dzieci wraz z rodzicem.
     */
    @Test
    @DisplayName("Cascade REMOVE usuwa dzieci wraz z rodzicem")
    void shouldDeleteChildrenWhenCascadeRemoveDeletesParent() {
        Long[] ids = LabJpa.inTransaction(factory, em -> {
            RemoveOnlyFolder folder = new RemoveOnlyFolder();
            RemoveOnlyNote note = folder.add("child");
            em.persist(folder);
            em.flush();
            return new Long[] { folder.id, note.id };
        });

        LabJpa.runInTransaction(factory, em -> em.remove(em.find(RemoveOnlyFolder.class, ids[0])));

        LabJpa.runInTransaction(factory, em -> assertThat(em.find(RemoveOnlyNote.class, ids[1])).isNull());
    }
}
