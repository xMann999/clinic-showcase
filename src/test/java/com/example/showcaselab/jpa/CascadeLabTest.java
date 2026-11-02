package com.example.showcaselab.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.showcaselab.jpa.CascadeModel.Folder;
import com.example.showcaselab.jpa.CascadeModel.Note;
import com.example.showcaselab.jpa.CascadeModel.PlainItem;
import com.example.showcaselab.jpa.CascadeModel.PlainOwner;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Każda operacja kaskadowa na małym prywatnym grafie Folder -> Note.
 * <h2>Cel</h2>
 * Obserwować propagację operacji persystencji na prywatny graf oraz skutek braku kaskady.
 * <h2>Jak działa przykład</h2>
 * Kaskada przenosi operację EntityManagera (persist, merge, remove, refresh, detach) z rodzica
 * na powiązane obiekty. ALL = wszystkie naraz. Bez kaskady każdą encję obsługuje się osobno.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Persist rodzica zapisuje dzieci.</li>
 * <li>Brak kaskady ujawnia odwołanie do niezapisanej encji.</li>
 * <li>Merge przenosi zmiany odłączonego grafu do bazy.</li>
 * <li>Usunięcie rodzica usuwa dzieci.</li>
 * <li>Refresh przywraca dzieci ze stanu bazy.</li>
 * <li>Detach odłącza rodzica i dzieci od kontekstu.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Wyniki dotyczą lokalnego Hibernate i H2. Modele równości są zwykłymi obiektami; przypisanie ID nie jest operacją persist.
 */
@DisplayName("Każda operacja kaskadowa na małym prywatnym grafie Folder -> Note.")
class CascadeLabTest {

    private static EntityManagerFactory factory;

    @BeforeAll
    static void start() {
        factory = LabJpa.create("cascade", CascadeModel.entities());
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    private Long folderWithNotes(String... texts) {
        return LabJpa.inTransaction(factory, em -> {
            Folder folder = new Folder("f");
            for (String text : texts) {
                folder.add(text);
            }
            em.persist(folder);
            return folder.id;
        });
    }

    private long notesOf(Long folderId) {
        return LabJpa.inTransaction(factory, em ->
            em
                .createQuery("select count(n) from CascadeNote n where n.folder.id = :id", Long.class)
                .setParameter("id", folderId)
                .getSingleResult()
        );
    }

    /**
     * Persist rodzica zapisuje dzieci.
     */
    @Test
    @DisplayName("Persist rodzica zapisuje dzieci")
    void shouldSaveChildrenWhenParentIsPersisted() {
        Long folderId = folderWithNotes("a", "b");
        assertThat(notesOf(folderId)).isEqualTo(2);
    }

    /**
     * Brak kaskady ujawnia odwołanie do niezapisanej encji.
     */
    @Test
    @DisplayName("Brak kaskady ujawnia odwołanie do niezapisanej encji")
    void shouldRejectTransientReferenceWhenCascadeIsAbsent() {
        assertThatThrownBy(() ->
            LabJpa.runInTransaction(factory, em -> {
                PlainItem item = new PlainItem();
                item.owner = new PlainOwner();
                em.persist(item);
                em.flush();
            })
        )
            .isInstanceOfAny(IllegalStateException.class, PersistenceException.class)
            .hasRootCauseInstanceOf(org.hibernate.TransientObjectException.class);
    }

    /**
     * Merge przenosi zmiany odłączonego grafu do bazy.
     */
    @Test
    @DisplayName("Merge przenosi zmiany odłączonego grafu do bazy")
    void shouldSaveGraphChangesWhenDetachedParentIsMerged() {
        Long folderId = folderWithNotes("old");
        Folder detached = LabJpa.inTransaction(factory, em -> {
            Folder folder = em.find(Folder.class, folderId);
            folder.notes.size();
            return folder;
        });
        detached.notes.getFirst().text = "new";

        LabJpa.runInTransaction(factory, em -> em.merge(detached));

        String text = LabJpa.inTransaction(factory, em -> em.find(Folder.class, folderId).notes.getFirst().text);
        assertThat(text).isEqualTo("new");
    }

    /**
     * Usunięcie rodzica usuwa dzieci.
     */
    @Test
    @DisplayName("Usunięcie rodzica usuwa dzieci")
    void shouldDeleteChildrenWhenParentIsRemoved() {
        Long folderId = folderWithNotes("a", "b");

        LabJpa.runInTransaction(factory, em -> em.remove(em.find(Folder.class, folderId)));

        assertThat(notesOf(folderId)).isZero();
    }

    /**
     * Refresh przywraca dzieci ze stanu bazy.
     */
    @Test
    @DisplayName("Refresh przywraca dzieci ze stanu bazy")
    void shouldReloadChildrenWhenParentIsRefreshed() {
        Long folderId = folderWithNotes("stored");
        LabJpa.runInTransaction(factory, em -> {
            Folder folder = em.find(Folder.class, folderId);
            folder.notes.getFirst().text = "unsaved change";

            em.refresh(folder);

            assertThat(folder.notes.getFirst().text).isEqualTo("stored");
        });
    }

    /**
     * Detach odłącza rodzica i dzieci od kontekstu.
     */
    @Test
    @DisplayName("Detach odłącza rodzica i dzieci od kontekstu")
    void shouldDetachChildrenWhenParentIsDetached() {
        Long folderId = folderWithNotes("a");
        try (EntityManager em = factory.createEntityManager()) {
            Folder folder = em.find(Folder.class, folderId);
            Note note = folder.notes.getFirst();

            em.detach(folder);

            assertThat(em.contains(folder)).isFalse();
            assertThat(em.contains(note)).isFalse();
        }
    }
}
