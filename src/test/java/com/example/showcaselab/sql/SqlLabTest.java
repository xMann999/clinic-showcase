package com.example.showcaselab.sql;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.showcaselab.support.LabDatabases;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/**
 * SQL na schemacie z migracji aplikacji.
 * <h2>Cel</h2>
 * Powiązać JOIN, grupowanie, granice czasu i ograniczenia z konkretnymi wierszami kontrolowanego fixture.
 * <h2>Jak działa przykład</h2>
 * Pliki: src/test/resources/labs/sql/clinic-fixture.sql, clinic-queries.sql
 * Fixture: Alfa ma dokładnie 6 wizyt (3 zajęte), Beta 2 wizyty 08.01 i 1 wizytę 09.01,
 * pacjenci Jan i Ola nie mają wizyt.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Przedział dnia wyklucza północ następnego dnia.</li>
 * <li>LEFT JOIN znajduje pacjentów bez wizyt.</li>
 * <li>HAVING filtruje lekarzy po liczbie wizyt.</li>
 * <li>Zapytanie wybiera wolne terminy.</li>
 * <li>JOIN pobiera adres konta pacjenta.</li>
 * <li>INSERT, UPDATE i DELETE zmieniają wskazane wiersze.</li>
 * <li>Klucz obcy i unikalność odrzucają niepoprawny zapis.</li>
 * <li>EXPLAIN zwraca plan zapytania dla H2.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Plan wykonania zależy od silnika i danych; test nie gwarantuje użycia konkretnego indeksu.
 */
@DisplayName("SQL na schemacie z migracji aplikacji.")
class SqlLabTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2030, 1, 8, 0, 0);

    private JdbcTemplate jdbc;
    private NamedParameterJdbcTemplate named;
    private Map<String, String> queries;

    /** Zamyka wyłącznie bazę utworzoną dla bieżącego scenariusza. */
    @AfterEach
    void closeDatabase() {
        if (jdbc != null) {
            jdbc.execute("shutdown");
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        DataSource dataSource = LabDatabases.dataSource(LabDatabases.memoryUrl("sql-lab"));
        LabDatabases.migrate(dataSource, LabDatabases.APP_CHANGELOG);
        new ResourceDatabasePopulator(new ClassPathResource("labs/sql/clinic-fixture.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        named = new NamedParameterJdbcTemplate(dataSource);
        queries = loadQueries();
    }

    private static Map<String, String> loadQueries() throws IOException {
        String text = new ClassPathResource("labs/sql/clinic-queries.sql").getContentAsString(StandardCharsets.UTF_8);
        Map<String, String> result = new LinkedHashMap<>();
        Arrays.stream(text.split("-- name: "))
            .skip(1)
            .forEach(block -> {
                String name = block.substring(0, block.indexOf('\n')).strip();
                String sql = block.substring(block.indexOf('\n') + 1).replaceAll("(?m)^--.*$", "").strip();
                result.put(name, sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql);
            });
        return result;
    }

    private long doctorId(String lastName) {
        return jdbc.queryForObject("select id from doctors where last_name = ?", Long.class, lastName);
    }

    /**
     * Przedział dnia wyklucza północ następnego dnia.
     * Wizyta Bety o północy 09.01 NIE należy do 08.01 (granica &lt; nextDay).
     */
    @Test
    @DisplayName("Przedział dnia wyklucza północ następnego dnia")
    void shouldExcludeNextMidnightWhenSelectingDay() {
        Map<String, Object> params = Map.of("doctorId", doctorId("Alfa"), "dayStart", DAY, "nextDay", DAY.plusDays(1));
        List<LocalDateTime> times = named.query(queries.get("doctor_visits_on_day"), params, (rs, row) ->
            rs.getTimestamp("date_time").toLocalDateTime()
        );

        assertThat(times).containsExactly(
            DAY.plusHours(8),
            DAY.plusHours(9),
            DAY.plusHours(10),
            DAY.plusHours(11),
            DAY.plusHours(12),
            DAY.plusHours(13)
        );

        Map<String, Object> beta = Map.of("doctorId", doctorId("Beta"), "dayStart", DAY, "nextDay", DAY.plusDays(1));
        assertThat(named.queryForList(queries.get("doctor_visits_on_day"), beta)).hasSize(2);
    }

    /**
     * LEFT JOIN znajduje pacjentów bez wizyt.
     */
    @Test
    @DisplayName("LEFT JOIN znajduje pacjentów bez wizyt")
    void shouldFindPatientsWhenNoVisitsExist() {
        assertThat(jdbc.queryForList(queries.get("patients_without_visits"), String.class)).containsExactly(
            "Jan",
            "Ola"
        );
    }

    /**
     * HAVING filtruje lekarzy po liczbie wizyt.
     */
    @Test
    @DisplayName("HAVING filtruje lekarzy po liczbie wizyt")
    void shouldFilterGroupsWhenVisitCountExceedsThreshold() {
        List<Map<String, Object>> rows = jdbc.queryForList(queries.get("busy_doctors"));

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().get("LAST_NAME")).isEqualTo("Alfa");
        assertThat(((Number) rows.getFirst().get("VISITS")).intValue()).isEqualTo(6);
    }

    /**
     * Zapytanie wybiera wolne terminy.
     */
    @Test
    @DisplayName("Zapytanie wybiera wolne terminy")
    void shouldFindUnassignedVisitsWhenSelectingFreeSlots() {
        assertThat(jdbc.queryForList(queries.get("free_slots"), Long.class)).hasSize(5);
    }

    /**
     * JOIN pobiera adres konta pacjenta.
     */
    @Test
    @DisplayName("JOIN pobiera adres konta pacjenta")
    void shouldFindOwnerEmailWhenJoiningPatientAccount() {
        assertThat(
            named.queryForObject(queries.get("patient_account_email"), Map.of("idCardNo", "SQL000001"), String.class)
        ).isEqualTo("family@lab.test");
    }

    /**
     * INSERT, UPDATE i DELETE zmieniają wskazane wiersze.
     */
    @Test
    @DisplayName("INSERT, UPDATE i DELETE zmieniają wskazane wiersze")
    void shouldChangeSelectedRowsWhenExecutingDml() {
        jdbc.update("insert into facilities (name, address) values (?, ?)", "Zeta", "Street 1");
        jdbc.update("insert into facilities (name, address) values (?, ?)", "Alfa", "Street 2");
        int updated = jdbc.update("update facilities set address = ? where name = ?", "Street 9", "Zeta");
        int deleted = jdbc.update("delete from facilities where name = ?", "Alfa");

        assertThat(updated).isEqualTo(1);
        assertThat(deleted).isEqualTo(1);
        assertThat(
            jdbc.queryForList("select name || ':' || address from facilities order by name", String.class)
        ).containsExactly("Zeta:Street 9");
    }

    /**
     * Klucz obcy i unikalność odrzucają niepoprawny zapis.
     */
    @Test
    @DisplayName("Klucz obcy i unikalność odrzucają niepoprawny zapis")
    void shouldRejectRowsWhenConstraintsAreViolated() {
        assertThatThrownBy(() ->
            jdbc.update(
                "insert into visits (date_time, duration_in_minutes, doctor_id) " +
                "values (TIMESTAMP '2030-01-10 10:00:00', 30, 987654)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() ->
            jdbc.update("insert into users (email, password_hash, role) values ('solo@lab.test', 'x', 'USER')")
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    /**
     * EXPLAIN zwraca plan zapytania dla H2.
     * Plan zależy od silnika i statystyk; na mikroskopijnej tabeli optymalizator może
     * wybrać pełny skan. Sprawdzamy, że plan istnieje - jego treść czytamy, nie obiecujemy.
     */
    @Test
    @DisplayName("EXPLAIN zwraca plan zapytania dla H2")
    void shouldReturnPlanWhenExplainingDayQuery() {
        String sql = queries
            .get("doctor_visits_on_day")
            .replace(":doctorId", String.valueOf(doctorId("Alfa")))
            .replace(":dayStart", "TIMESTAMP '2030-01-08 00:00:00'")
            .replace(":nextDay", "TIMESTAMP '2030-01-09 00:00:00'");
        String plan = jdbc.queryForObject("explain " + sql, String.class);

        assertThat(plan).containsIgnoringCase("visits");
    }
}
