package com.example.showcaselab.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.showcaselab.support.LabDatabases;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Expand/contract - zmiana formatu telefonu bez jednorazowego "przełączenia".
 * <h2>Cel</h2>
 * Prześledzić okres zgodności dwóch wersji schematu i warunek bezpiecznego usunięcia starej kolumny.
 * <h2>Jak działa przykład</h2>
 * Pliki: src/test/resources/labs/liquibase/evolution/
 * 1. expand: dodajemy NOWĄ kolumnę phone_e164 obok starej phone (stara aplikacja dalej działa);
 * 2. migrate: backfill istniejących danych; w okresie zgodności nowa wersja aplikacji zapisuje
 *    obie kolumny, stara - tylko phone (backfill ma runAlways, więc dociąga zaległe wiersze);
 * 3. contract: usuwamy starą kolumnę dopiero, gdy spełnione jest kryterium (precondition:
 *    brak wierszy bez nowej wartości). Inaczej Liquibase przerywa (HALT).
 * Obecność trzech plików nie dowodzi "zero downtime" - to zależy też od wdrożeń aplikacji.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Rozszerzenie, migracja danych i usunięcie zachowują zgodność.</li>
 * <li>Przedwczesne usunięcie kolumny zatrzymuje warunek wstępny.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Zmiany dotyczą wyłącznie laboratoryjnych baz. Usunięcie tabeli podczas rollback usuwa również jej dane.
 */
@DisplayName("Expand/contract - zmiana formatu telefonu bez jednorazowego 'przełączenia'.")
class ExpandContractLabTest {

    private DataSource dataSource;
    private JdbcTemplate jdbc;

    /** Zamyka wyłącznie bazę utworzoną dla bieżącego scenariusza. */
    @AfterEach
    void closeDatabase() {
        if (jdbc != null) {
            jdbc.execute("shutdown");
        }
    }

    @BeforeEach
    void setUp() {
        dataSource = LabDatabases.dataSource(LabDatabases.memoryUrl("evolution"));
        jdbc = new JdbcTemplate(dataSource);
        LabDatabases.migrate(dataSource, "classpath:labs/liquibase/evolution/phase1.yaml");
        jdbc.update("insert into contacts (name, phone) values ('Anna', '600 700 800')");
        jdbc.update("insert into contacts (name, phone) values ('Jan', '500 100 200')");
    }

    private List<String> columns() {
        return jdbc.queryForList(
            "select lower(column_name) from information_schema.columns " +
            "where table_name = 'CONTACTS' order by ordinal_position",
            String.class
        );
    }

    /**
     * Rozszerzenie, migracja danych i usunięcie zachowują zgodność.
     * Okres zgodności: stara wersja aplikacji zna tylko phone, nowa zapisuje obie kolumny.
     */
    @Test
    @DisplayName("Rozszerzenie, migracja danych i usunięcie zachowują zgodność")
    void shouldPreserveDataWhenSchemaEvolvesInPhases() {
        LabDatabases.migrate(dataSource, "classpath:labs/liquibase/evolution/phase2.yaml");
        assertThat(columns()).containsExactly("id", "name", "phone", "phone_e164");
        assertThat(jdbc.queryForList("select phone_e164 from contacts order by id", String.class)).containsExactly(
            "+48600700800",
            "+48500100200"
        );

        jdbc.update("insert into contacts (name, phone) values ('Old app', '601 000 000')");
        jdbc.update("insert into contacts (name, phone, phone_e164) values ('New app', '602 000 000', '+48602000000')");

        LabDatabases.migrate(dataSource, "classpath:labs/liquibase/evolution/phase3.yaml");

        assertThat(columns()).containsExactly("id", "name", "phone_e164");
        assertThat(jdbc.queryForList("select phone_e164 from contacts order by id", String.class)).containsExactly(
            "+48600700800",
            "+48500100200",
            "+48601000000",
            "+48602000000"
        );
    }

    /**
     * Przedwczesne usunięcie kolumny zatrzymuje warunek wstępny.
     * Plik bez kroku backfill: kryterium usunięcia starej kolumny nie jest spełnione.
     */
    @Test
    @DisplayName("Przedwczesne usunięcie kolumny zatrzymuje warunek wstępny")
    void shouldKeepOldColumnWhenBackfillIsIncomplete() {
        LabDatabases.migrate(dataSource, "classpath:labs/liquibase/evolution/phase2.yaml");
        jdbc.update("insert into contacts (name, phone) values ('Old app', '601 000 000')");

        assertThatThrownBy(() ->
            LabDatabases.migrate(dataSource, "classpath:labs/liquibase/evolution/contract-only.yaml")
        ).isInstanceOf(IllegalStateException.class);
        assertThat(columns()).contains("phone", "phone_e164");
    }
}
