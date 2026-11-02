package com.example.showcaselab.dbinit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.showcaselab.support.LabDatabases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceInitializationAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Inne niż Liquibase sposoby inicjalizacji schematu - na jednorazowych bazach.
 * <h2>Cel</h2>
 * Porównać kolejność tworzenia schematu i ładowania danych oraz rozpoznać przyczynę nieudanego startu.
 * <h2>Jak działa przykład</h2>
 * - schema.sql + data.sql (spring.sql.init.*): skrypty uruchamiane przy każdym starcie;
 * - spring.jpa.hibernate.ddl-auto: none (nic), validate (tylko sprawdza), update (dopisuje braki,
 *   nigdy nie usuwa), create (tworzy od nowa), create-drop (tworzy i usuwa przy zamknięciu);
 * - defer-datasource-initialization: data.sql dopiero PO utworzeniu tabel przez Hibernate.
 * W aplikacji żadna z tych dróg nie działa równolegle z Liquibase - właścicielem schematu jest
 * jeden mechanizm, a Hibernate tylko waliduje.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Skrypt schematu poprzedza skrypt danych.</li>
 * <li>Odroczenie danych pozwala najpierw utworzyć tabele.</li>
 * <li>Brak odroczenia uruchamia dane przed tabelami.</li>
 * <li>Walidacja pustego schematu zatrzymuje start.</li>
 * <li>Brak inicjalizacji pozostawia bazę bez tabeli.</li>
 * <li>Aktualizacja schematu tworzy brakującą tabelę.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Każdy kontekst ma osobną bazę H2. Opcja update nie zastępuje wersjonowanych migracji.
 */
@DisplayName("Inne niż Liquibase sposoby inicjalizacji schematu - na jednorazowych bazach.")
class DatabaseInitializationLabTest {

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class LabEntities {}

    private ApplicationContextRunner jdbcOnly() {
        return new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    DataSourceInitializationAutoConfiguration.class,
                    JdbcTemplateAutoConfiguration.class
                )
            )
            .withPropertyValues(
                "spring.datasource.url=" +
                LabDatabases.memoryUrl("init").replace("DB_CLOSE_DELAY=-1", "DB_CLOSE_DELAY=0")
            );
    }

    private ApplicationContextRunner withJpa(String ddlAuto) {
        return jdbcOnly()
            .withConfiguration(
                AutoConfigurations.of(HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class)
            )
            .withUserConfiguration(LabEntities.class)
            .withPropertyValues("spring.jpa.hibernate.ddl-auto=" + ddlAuto);
    }

    private static int rooms(org.springframework.context.ApplicationContext context) {
        return context.getBean(JdbcTemplate.class).queryForObject("select count(*) from lab_room", Integer.class);
    }

    /**
     * Skrypt schematu poprzedza skrypt danych.
     */
    @Test
    @DisplayName("Skrypt schematu poprzedza skrypt danych")
    void shouldLoadRowsWhenSchemaScriptRunsFirst() {
        jdbcOnly()
            .withPropertyValues(
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:labs/init/schema.sql",
                "spring.sql.init.data-locations=classpath:labs/init/data.sql"
            )
            .run(context -> assertThat(rooms(context)).isEqualTo(2));
    }

    /**
     * Odroczenie danych pozwala najpierw utworzyć tabele.
     */
    @Test
    @DisplayName("Odroczenie danych pozwala najpierw utworzyć tabele")
    void shouldLoadRowsWhenDataInitializationIsDeferred() {
        withJpa("create-drop")
            .withPropertyValues(
                "spring.sql.init.mode=always",
                "spring.sql.init.data-locations=classpath:labs/init/data.sql",
                "spring.jpa.defer-datasource-initialization=true"
            )
            .run(context -> assertThat(rooms(context)).isEqualTo(2));
    }

    /**
     * Brak odroczenia uruchamia dane przed tabelami.
     */
    @Test
    @DisplayName("Brak odroczenia uruchamia dane przed tabelami")
    void shouldFailWhenDataRunsBeforeSchemaCreation() {
        withJpa("create-drop")
            .withPropertyValues(
                "spring.sql.init.mode=always",
                "spring.sql.init.data-locations=classpath:labs/init/data.sql"
            )
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("LAB_ROOM");
            });
    }

    /**
     * Walidacja pustego schematu zatrzymuje start.
     */
    @Test
    @DisplayName("Walidacja pustego schematu zatrzymuje start")
    void shouldFailWhenValidatingAnEmptySchema() {
        withJpa("validate").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("lab_room");
        });
    }

    /**
     * Brak inicjalizacji pozostawia bazę bez tabeli.
     */
    @Test
    @DisplayName("Brak inicjalizacji pozostawia bazę bez tabeli")
    void shouldLeaveTableAbsentWhenInitializationIsDisabled() {
        withJpa("none").run(context ->
            assertThatThrownBy(() -> rooms(context)).isInstanceOf(BadSqlGrammarException.class)
        );
    }

    /**
     * Aktualizacja schematu tworzy brakującą tabelę.
     */
    @Test
    @DisplayName("Aktualizacja schematu tworzy brakującą tabelę")
    void shouldCreateTableWhenUpdatingEmptySchema() {
        withJpa("update").run(context -> assertThat(rooms(context)).isZero());
    }
}
