package com.example.showcaselab.jpa;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * Małe, prywatne grafy obiektów do laboratoriów kaskad. Celowo NIE używamy encji przychodni:
 * konto, pacjent i placówka są współdzielone, więc w aplikacji nie mają kaskad REMOVE.
 * Notatka w teczce jest "prywatnym dzieckiem" - bez teczki nie ma sensu.
 */
final class CascadeModel {

    private CascadeModel() {}

    /** Teczka z kaskadą ALL i orphanRemoval: notatki żyją i giną razem z nią. */
    @Entity(name = "CascadeFolder")
    static class Folder {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String name;

        @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true)
        List<Note> notes = new ArrayList<>();

        protected Folder() {}

        Folder(String name) {
            this.name = name;
        }

        Note add(String text) {
            Note note = new Note(text, this);
            notes.add(note);
            return note;
        }

        void remove(Note note) {
            notes.remove(note);
            note.folder = null;
        }
    }

    @Entity(name = "CascadeNote")
    static class Note {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String text;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "folder_id")
        Folder folder;

        protected Note() {}

        Note(String text, Folder folder) {
            this.text = text;
            this.folder = folder;
        }
    }

    /** Kaskada PERSIST + REMOVE, ale BEZ orphanRemoval: odpięcie nie usuwa dziecka. */
    @Entity(name = "RemoveOnlyFolder")
    static class RemoveOnlyFolder {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        @OneToMany(mappedBy = "folder", cascade = { CascadeType.PERSIST, CascadeType.REMOVE })
        List<RemoveOnlyNote> notes = new ArrayList<>();

        RemoveOnlyNote add(String text) {
            RemoveOnlyNote note = new RemoveOnlyNote();
            note.text = text;
            note.folder = this;
            notes.add(note);
            return note;
        }
    }

    @Entity(name = "RemoveOnlyNote")
    static class RemoveOnlyNote {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String text;

        @ManyToOne
        @JoinColumn(name = "folder_id")
        RemoveOnlyFolder folder;
    }

    /** Brak jakiejkolwiek kaskady - jak relacje do współdzielonych encji w aplikacji. */
    @Entity(name = "PlainOwner")
    static class PlainOwner {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String name = "owner";
    }

    @Entity(name = "PlainItem")
    static class PlainItem {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        @ManyToOne(optional = false)
        @JoinColumn(name = "owner_id", nullable = false)
        PlainOwner owner;
    }

    static Class<?>[] entities() {
        return new Class<?>[] {
            Folder.class,
            Note.class,
            RemoveOnlyFolder.class,
            RemoveOnlyNote.class,
            PlainOwner.class,
            PlainItem.class,
        };
    }
}
