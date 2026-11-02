package com.example.showcaselab.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Relacja 1:1 - właściciel kontra mappedBy, @MapsId, tabela łącząca, oraz zmierzone zachowanie LAZY po stronie odwrotnej.
 * <h2>Cel</h2>
 * Rozpoznać stronę zapisującą klucz obcy i porównać warianty klucza współdzielonego oraz tabeli łączącej.
 * <h2>Jak działa przykład</h2>
 * oraz zmierzone zachowanie LAZY po stronie odwrotnej.
 * W aplikacji Doctor -> User jest jednokierunkowe (Doctor ma kolumnę user_id). Tu dokładamy
 * stronę odwrotną (Account.profile z mappedBy), by pokazać, która strona naprawdę zapisuje FK.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Zmiana strony odwrotnej nie zapisuje klucza obcego.</li>
 * <li>Zmiana właściciela zapisuje klucz obcy.</li>
 * <li>Strona odwrotna jest ładowana mimo LAZY w tej konfiguracji.</li>
 * <li>MapsId współdzieli klucz konta i profilu.</li>
 * <li>Tabela łącząca przechowuje powiązanie.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Wyniki dotyczą lokalnego Hibernate i H2. Modele równości są zwykłymi obiektami; przypisanie ID nie jest operacją persist.
 */
@DisplayName(
    "Relacja 1:1 - właściciel kontra mappedBy, @MapsId, tabela łącząca, oraz zmierzone zachowanie LAZY po stronie odwrotnej."
)
class OneToOneLabTest {

    @Entity(name = "LabAccount")
    static class Account {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String email;

        /** Strona odwrotna: nie ma kolumny; LAZY jest tu tylko życzeniem (patrz test niżej). */
        @OneToOne(mappedBy = "account", fetch = FetchType.LAZY)
        Profile profile;
    }

    @Entity(name = "LabProfile")
    static class Profile {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String bio;

        /** Właściciel: kolumna account_id w tabeli profilu. */
        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "account_id", unique = true)
        Account account;
    }

    /** @MapsId: klucz główny profilu = klucz konta (współdzielony PK, bez osobnej kolumny FK). */
    @Entity(name = "LabSharedProfile")
    static class SharedProfile {

        @Id
        Long id;

        String bio;

        @MapsId
        @OneToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "account_id")
        Account account;
    }

    /** 1:1 przez tabelę łączącą - żadna z tabel encji nie ma kolumny FK. */
    @Entity(name = "LabBadgeHolder")
    static class BadgeHolder {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        @OneToOne
        @JoinTable(
            name = "holder_badge",
            joinColumns = @JoinColumn(name = "holder_id", unique = true),
            inverseJoinColumns = @JoinColumn(name = "badge_id", unique = true)
        )
        Badge badge;
    }

    @Entity(name = "LabBadge")
    static class Badge {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        Long id;

        String label = "VIP";
    }

    private static EntityManagerFactory factory;

    @BeforeAll
    static void start() {
        factory = LabJpa.create(
            "one-to-one",
            Account.class,
            Profile.class,
            SharedProfile.class,
            BadgeHolder.class,
            Badge.class
        );
    }

    @AfterAll
    static void stop() {
        factory.close();
    }

    /**
     * Zmiana strony odwrotnej nie zapisuje klucza obcego.
     */
    @Test
    @DisplayName("Zmiana strony odwrotnej nie zapisuje klucza obcego")
    void shouldLeaveForeignKeyEmptyWhenOnlyInverseSideChanges() {
        Long profileId = LabJpa.inTransaction(factory, em -> {
            Account account = new Account();
            Profile profile = new Profile();
            account.profile = profile;
            em.persist(account);
            em.persist(profile);
            return profile.id;
        });

        LabJpa.runInTransaction(factory, em -> assertThat(em.find(Profile.class, profileId).account).isNull());
    }

    /**
     * Zmiana właściciela zapisuje klucz obcy.
     * Pułapka: profile.account to LENIWE PROXY. Bezpośredni odczyt pola proxy (account.id)
     * zwraca null - pola proxy są puste. Dane ma obiekt docelowy (unproxy) albo getter.
     */
    @Test
    @DisplayName("Zmiana właściciela zapisuje klucz obcy")
    void shouldWriteForeignKeyWhenOwningSideChanges() {
        Long[] ids = LabJpa.inTransaction(factory, em -> {
            Account account = new Account();
            Profile profile = new Profile();
            profile.account = account;
            em.persist(account);
            em.persist(profile);
            return new Long[] { account.id, profile.id };
        });

        LabJpa.runInTransaction(factory, em -> {
            Profile profile = em.find(Profile.class, ids[1]);

            assertThat(profile.account.id).isNull();
            assertThat(((Account) Hibernate.unproxy(profile.account)).id).isEqualTo(ids[0]);
        });
    }

    /**
     * Strona odwrotna jest ładowana mimo LAZY w tej konfiguracji.
     * Hibernate nie wie bez zapytania, czy profil istnieje (null czy proxy?), więc
     * dociąga go od razu - adnotacja LAZY po stronie mappedBy tego nie zmienia.
     */
    @Test
    @DisplayName("Strona odwrotna jest ładowana mimo LAZY w tej konfiguracji")
    void shouldLoadInverseSideWhenEnhancementIsAbsent() {
        Long accountId = LabJpa.inTransaction(factory, em -> {
            Account account = new Account();
            Profile profile = new Profile();
            profile.account = account;
            em.persist(account);
            em.persist(profile);
            return account.id;
        });

        LabJpa.runInTransaction(factory, em -> {
            Account account = em.find(Account.class, accountId);

            assertThat(account.profile).isNotNull();
            assertThat(Hibernate.isInitialized(account.profile)).isTrue();
        });
    }

    /**
     * MapsId współdzieli klucz konta i profilu.
     */
    @Test
    @DisplayName("MapsId współdzieli klucz konta i profilu")
    void shouldSharePrimaryKeyWhenMapsIdIsUsed() {
        Long[] ids = LabJpa.inTransaction(factory, em -> {
            Account account = new Account();
            em.persist(account);
            SharedProfile profile = new SharedProfile();
            profile.account = account;
            em.persist(profile);
            return new Long[] { account.id, profile.id };
        });

        assertThat(ids[1]).isEqualTo(ids[0]);
        LabJpa.runInTransaction(factory, em -> {
            SharedProfile reloaded = em.find(SharedProfile.class, ids[1]);
            assertThat(reloaded.id).isEqualTo(ids[0]);
            assertThat(((Account) Hibernate.unproxy(reloaded.account)).id).isEqualTo(ids[0]);
        });
    }

    /**
     * Tabela łącząca przechowuje powiązanie.
     */
    @Test
    @DisplayName("Tabela łącząca przechowuje powiązanie")
    void shouldStoreAssociationWhenJoinTableIsUsed() {
        LabJpa.runInTransaction(factory, em -> {
            Badge badge = new Badge();
            em.persist(badge);
            BadgeHolder holder = new BadgeHolder();
            holder.badge = badge;
            em.persist(holder);
        });

        long rows = LabJpa.inTransaction(factory, em ->
            ((Number) em.createNativeQuery("select count(*) from holder_badge").getSingleResult()).longValue()
        );
        assertThat(rows).isEqualTo(1);
    }
}
