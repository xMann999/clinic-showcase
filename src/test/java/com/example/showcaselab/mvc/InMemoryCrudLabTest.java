package com.example.showcaselab.mvc;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Etap "CRUD na kolekcji w pamięci" - zanim pojawi się baza.
 * <h2>Cel</h2>
 * Pokazać kontrakt CRUD oraz brak trwałości magazynu opartego wyłącznie na kolekcji.
 * <h2>Jak działa przykład</h2>
 * Kontrakt (create/find/update/delete) jest taki sam jak w repozytorium JPA, ale:
 * - stan żyje w obiekcie Javy - nowa instancja (restart aplikacji) zaczyna od zera;
 * - brak transakcji, ograniczeń UNIQUE/FK i zapytań; ConcurrentHashMap chroni tylko pojedyncze
 *   operacje mapy, a nie sekwencję "sprawdź, potem zapisz".
 * To nie jest druga wersja aplikacji - tylko punkt odniesienia dla JPA.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Operacje CRUD zmieniają lokalny stan kolekcji.</li>
 * <li>Nowa instancja magazynu nie dziedziczy danych.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Magazyn pamięciowy nie ma trwałości ani transakcji. Standalone MockMvc nie uruchamia całej konfiguracji aplikacji.
 */
@DisplayName("Etap 'CRUD na kolekcji w pamięci' - zanim pojawi się baza.")
class InMemoryCrudLabTest {

    record PatientRecord(Long id, String lastName, LocalDate birthday) {}

    static final class InMemoryPatientStore {

        private final Map<Long, PatientRecord> data = new ConcurrentHashMap<>();
        private final AtomicLong sequence = new AtomicLong();

        PatientRecord create(String lastName, LocalDate birthday) {
            PatientRecord record = new PatientRecord(sequence.incrementAndGet(), lastName, birthday);
            data.put(record.id(), record);
            return record;
        }

        Optional<PatientRecord> find(Long id) {
            return Optional.ofNullable(data.get(id));
        }

        List<PatientRecord> findAll() {
            return data.values().stream().sorted(java.util.Comparator.comparing(PatientRecord::id)).toList();
        }

        Optional<PatientRecord> update(Long id, String lastName) {
            return Optional.ofNullable(
                data.computeIfPresent(id, (key, old) -> new PatientRecord(key, lastName, old.birthday()))
            );
        }

        boolean delete(Long id) {
            return data.remove(id) != null;
        }
    }

    /**
     * Operacje CRUD zmieniają lokalny stan kolekcji.
     */
    @Test
    @DisplayName("Operacje CRUD zmieniają lokalny stan kolekcji")
    void shouldUpdateLocalStateWhenCrudOperationsRun() {
        InMemoryPatientStore store = new InMemoryPatientStore();
        PatientRecord created = store.create("Nowak", LocalDate.of(1990, 1, 1));

        assertThat(store.find(created.id())).contains(created);
        assertThat(store.update(created.id(), "Kowalska")).map(PatientRecord::lastName).contains("Kowalska");
        assertThat(store.update(999L, "X")).isEmpty();
        assertThat(store.delete(created.id())).isTrue();
        assertThat(store.delete(created.id())).isFalse();
        assertThat(store.findAll()).isEmpty();
    }

    /**
     * Nowa instancja magazynu nie dziedziczy danych.
     */
    @Test
    @DisplayName("Nowa instancja magazynu nie dziedziczy danych")
    void shouldStartEmptyWhenStoreIsRecreated() {
        InMemoryPatientStore beforeRestart = new InMemoryPatientStore();
        beforeRestart.create("Nowak", LocalDate.of(1990, 1, 1));

        InMemoryPatientStore afterRestart = new InMemoryPatientStore();

        assertThat(afterRestart.findAll()).isEmpty();
    }
}
