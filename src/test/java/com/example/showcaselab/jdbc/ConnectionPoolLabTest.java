package com.example.showcaselab.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLTransientConnectionException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pula połączeń HikariCP i JdbcTemplate.
 * <h2>Cel</h2>
 * Rozróżnić oczekiwanie na połączenie od wykonania SQL i sprawdzić zwrot zasobu do puli.
 * <h2>Jak działa przykład</h2>
 * DataSource to fabryka połączeń; Hikari trzyma gotowe połączenia i wypożycza je wątkom.
 * W aplikacji te same ustawienia to spring.datasource.hikari.maximum-pool-size
 * i spring.datasource.hikari.connection-timeout (tu wartości są skrajnie małe - tylko dla labu).
 * connectionTimeout = jak długo czekać na WOLNE połączenie z puli.
 * To nie jest timeout wykonywania SQL (ten ustawia się np. JdbcTemplate.setQueryTimeout).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Zajęta pula odrzuca pobranie, a zwrot pozwala użyć połączenia ponownie.</li>
 * <li>JdbcTemplate mapuje wiersze i oddaje połączenie.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Timeout pobrania połączenia nie ogranicza czasu wykonywania zapytania SQL.
 */
@DisplayName("Pula połączeń HikariCP i JdbcTemplate.")
class ConnectionPoolLabTest {

    private HikariDataSource dataSource;

    @BeforeEach
    void createPool() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:mem:pool-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=0");
        config.setUsername("sa");
        config.setPassword("");
        config.setPoolName("lab-pool");
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(500);
        dataSource = new HikariDataSource(config);
    }

    @AfterEach
    void closePool() {
        dataSource.close();
    }

    /**
     * Zajęta pula odrzuca pobranie, a zwrot pozwala użyć połączenia ponownie.
     * Szeroki limit chroni test przed zawieszeniem; nie sprawdzamy dokładnych 500 ms.
     */
    @Test
    @DisplayName("Zajęta pula odrzuca pobranie, a zwrot pozwala użyć połączenia ponownie")
    void shouldAllowReuseWhenBorrowedConnectionIsReturned() throws Exception {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            try (Connection first = dataSource.getConnection()) {
                assertThat(first.isValid(1)).isTrue();
                assertThatThrownBy(dataSource::getConnection).isInstanceOf(SQLTransientConnectionException.class);
            }
            try (Connection again = dataSource.getConnection()) {
                assertThat(again.isValid(1)).isTrue();
            }
        });
    }

    record FacilityRow(long id, String name) {}

    /**
     * JdbcTemplate mapuje wiersze i oddaje połączenie.
     * JdbcTemplate: pobiera połączenie, tworzy PreparedStatement z parametrami (?),
     * iteruje ResultSet i ZAWSZE zamyka zasoby oraz oddaje połączenie do puli.
     */
    @Test
    @DisplayName("JdbcTemplate mapuje wiersze i oddaje połączenie")
    void shouldReleaseConnectionWhenJdbcTemplateMapsRows() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table facilities (id bigint primary key, name varchar(50))");
        jdbc.update("insert into facilities values (?, ?)", 1, "North");
        jdbc.update("insert into facilities values (?, ?)", 2, "South");

        List<FacilityRow> rows = jdbc.query(
            "select id, name from facilities where name like ? order by id",
            (rs, rowNum) -> new FacilityRow(rs.getLong("id"), rs.getString("name")),
            "%th"
        );

        assertThat(rows).containsExactly(new FacilityRow(1, "North"), new FacilityRow(2, "South"));
        assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
    }
}
