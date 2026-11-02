package com.example.showcaselab.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Kontrakt equals/hashCode i dlaczego encje go łatwo łamią.
 * <h2>Cel</h2>
 * Powiązać stabilność hashCode i kontrakt equals z odnajdywaniem obiektów w HashSet.
 * <h2>Jak działa przykład</h2>
 * HashSet szuka elementu w kubełku wyznaczonym przez hashCode z chwili WSTAWIENIA.
 * Jeśli hash zmieni się później, contains() zagląda do innego kubełka i "gubi" element.
 * Strategia aplikacji (encje com.example.clinic.domain): równość po id nadanym przez bazę,
 * hash stały dla typu encji, zgodny z proxy - dowody w JpaMappingTest.
 * Klucz naturalny (np. numer dokumentu) bywa kuszący, ale dokument można wymienić -
 * nie jest "zawsze niezmienny"; UUID nadany w konstruktorze jest stabilny od początku,
 * kosztem większego klucza i indeksu.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Zmiana nazwiska psuje wyszukiwanie przy mutowalnym hashu.</li>
 * <li>Nadanie identyfikatora psuje wyszukiwanie przy hashu z ID.</li>
 * <li>Stały hash zachowuje wyszukiwanie i symetrię równości.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Wyniki dotyczą lokalnego Hibernate i H2. Modele równości są zwykłymi obiektami; przypisanie ID nie jest operacją persist.
 */
@DisplayName("Kontrakt equals/hashCode i dlaczego encje go łatwo łamią.")
class EntityEqualityLabTest {

    /** Błąd 1: hash z mutowalnego pola. */
    static final class HashOnName {

        String lastName;

        HashOnName(String lastName) {
            this.lastName = lastName;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof HashOnName that && Objects.equals(lastName, that.lastName);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(lastName);
        }
    }

    /** Błąd 2: hash z id, które baza nadaje dopiero przy persist (null -> liczba). */
    static final class HashOnGeneratedId {

        Long id;

        @Override
        public boolean equals(Object other) {
            return other instanceof HashOnGeneratedId that && id != null && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(id);
        }
    }

    /** Strategia jak w aplikacji: równość po nie-null id, hash stały dla typu. */
    static final class StableHash {

        Long id;
        String lastName;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            return other instanceof StableHash that && id != null && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            return StableHash.class.hashCode();
        }
    }

    /**
     * Zmiana nazwiska psuje wyszukiwanie przy mutowalnym hashu.
     */
    @Test
    @DisplayName("Zmiana nazwiska psuje wyszukiwanie przy mutowalnym hashu")
    void shouldLoseLookupWhenMutableHashChanges() {
        HashOnName patient = new HashOnName("Nowak");
        Set<HashOnName> set = new HashSet<>(Set.of(patient));

        patient.lastName = "Kowalska";

        assertThat(set.contains(patient)).isFalse();
    }

    /**
     * Nadanie identyfikatora psuje wyszukiwanie przy hashu z ID.
     */
    @Test
    @DisplayName("Nadanie identyfikatora psuje wyszukiwanie przy hashu z ID")
    void shouldLoseLookupWhenGeneratedIdChangesHash() {
        HashOnGeneratedId patient = new HashOnGeneratedId();
        Set<HashOnGeneratedId> set = new HashSet<>();
        set.add(patient);

        patient.id = 42L;

        assertThat(set.contains(patient)).isFalse();
    }

    /**
     * Stały hash zachowuje wyszukiwanie i symetrię równości.
     */
    @Test
    @DisplayName("Stały hash zachowuje wyszukiwanie i symetrię równości")
    void shouldPreserveSetMembershipWhenStableHashIsUsed() {
        StableHash first = new StableHash();
        StableHash second = new StableHash();
        Set<StableHash> set = new HashSet<>();
        set.add(first);

        first.id = 42L;
        first.lastName = "Kowalska";

        assertThat(set.contains(first)).isTrue();
        assertThat(first).isNotEqualTo(second);
        assertThat(new StableHash()).isNotEqualTo(new StableHash());
        StableHash sameRow = new StableHash();
        sameRow.id = 42L;
        assertThat(sameRow).isEqualTo(first).hasSameHashCodeAs(first);
        assertThat(first).isEqualTo(sameRow);
        assertThat(set).contains(sameRow);
        assertThat(set.add(sameRow)).isFalse();
    }
}
