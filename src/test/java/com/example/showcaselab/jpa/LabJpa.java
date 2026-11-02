package com.example.showcaselab.jpa;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Izolowana jednostka persystencji dla laboratoriów JPA - bez Springa i bez encji aplikacji.
 *
 * PersistenceConfiguration (JPA 3.2) zastępuje plik persistence.xml: podajemy klasy encji,
 * URL bazy i akcję schematu. "drop-and-create" (odtworzenie schematu przy uruchomieniu) jest tu
 * dopuszczalne, bo laboratorium pokazuje mapowanie, a nie migracje - aplikacja ma Liquibase.
 * Statystyki Hibernate pozwalają POLICZYĆ zapytania zamiast zgadywać.
 */
public final class LabJpa {

    private LabJpa() {}

    public static EntityManagerFactory create(String name, Class<?>... entities) {
        PersistenceConfiguration configuration = new PersistenceConfiguration(name)
            .property(
                PersistenceConfiguration.JDBC_URL,
                "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=0"
            )
            .property(PersistenceConfiguration.JDBC_USER, "sa")
            .property(PersistenceConfiguration.JDBC_PASSWORD, "")
            .property(PersistenceConfiguration.SCHEMAGEN_DATABASE_ACTION, "drop-and-create")
            .property("hibernate.generate_statistics", "true");
        for (Class<?> entity : entities) {
            configuration.managedClass(entity);
        }
        return configuration.createEntityManagerFactory();
    }

    public static Statistics statistics(EntityManagerFactory factory) {
        return factory.unwrap(SessionFactory.class).getStatistics();
    }

    /** Jedna transakcja w nowym EntityManager - po wyjściu kontekst trwałości jest zamknięty. */
    public static <T> T inTransaction(EntityManagerFactory factory, Function<EntityManager, T> work) {
        try (EntityManager em = factory.createEntityManager()) {
            em.getTransaction().begin();
            try {
                T result = work.apply(em);
                em.getTransaction().commit();
                return result;
            } catch (RuntimeException | Error exception) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
                throw exception;
            }
        }
    }

    public static void runInTransaction(EntityManagerFactory factory, Consumer<EntityManager> work) {
        inTransaction(factory, em -> {
            work.accept(em);
            return null;
        });
    }
}
