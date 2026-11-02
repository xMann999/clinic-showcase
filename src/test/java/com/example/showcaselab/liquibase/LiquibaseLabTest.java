package com.example.showcaselab.liquibase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.example.clinic.ClinicShowcaseApplication;
import com.example.showcaselab.support.LabDatabases;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.ValidationFailedException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Historia Liquibase - ponowny start, checksum, rollback i kolejność względem Hibernate.
 * <h2>Cel</h2>
 * Obserwować historię migracji, kontrolę zmienionego changesetu i skutki rollbacku na własnej bazie.
 * <h2>Jak działa przykład</h2>
 * Bazy są jednorazowe (plik w katalogu tymczasowym albo pamięć), nigdy baza aplikacji.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Ponowny start nie wykonuje zastosowanych zmian.</li>
 * <li>Zmiana zastosowanego changesetu narusza sumę kontrolną.</li>
 * <li>Rollback cofa zmiany i usuwa dane wraz z tabelą.</li>
 * <li>Walidacja Hibernate wykrywa brak migracji.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Zmiany dotyczą wyłącznie laboratoryjnych baz. Usunięcie tabeli podczas rollback usuwa również jej dane.
 */
@DisplayName("Historia Liquibase - ponowny start, checksum, rollback i kolejność względem Hibernate.")
class LiquibaseLabTest {

    @TempDir
    Path dir;

    /**
     * Ponowny start nie wykonuje zastosowanych zmian.
     * Ta sama baza plikowa: drugi start porównał changelog z tabelą i nie wykonał niczego.
     */
    @Test
    @DisplayName("Ponowny start nie wykonuje zastosowanych zmian")
    void shouldSkipAppliedChangesWhenMigrationRunsAgain() {
        DataSource dataSource = LabDatabases.dataSource("jdbc:h2:file:" + dir.resolve("clinic").toAbsolutePath());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        LabDatabases.migrate(dataSource, LabDatabases.APP_CHANGELOG);
        List<Map<String, Object>> afterFirst = jdbc.queryForList(
            "select id, author, filename, dateexecuted, md5sum from databasechangelog order by orderexecuted"
        );
        LabDatabases.migrate(dataSource, LabDatabases.APP_CHANGELOG);
        List<Map<String, Object>> afterSecond = jdbc.queryForList(
            "select id, author, filename, dateexecuted, md5sum from databasechangelog order by orderexecuted"
        );

        assertThat(afterFirst).hasSize(10);
        assertThat(afterSecond).isEqualTo(afterFirst);
    }

    /**
     * Zmiana zastosowanego changesetu narusza sumę kontrolną.
     * v2 ma TEN SAM id/author/logicalFilePath, ale inną długość kolumny - to inna treść zmian.
     */
    @Test
    @DisplayName("Zmiana zastosowanego changesetu narusza sumę kontrolną")
    void shouldRejectChecksumWhenAppliedChangesetIsEdited() {
        DataSource dataSource = LabDatabases.dataSource("jdbc:h2:file:" + dir.resolve("checksum").toAbsolutePath());
        LabDatabases.migrate(dataSource, "classpath:labs/liquibase/checksum/v1.yaml");

        Throwable failure = catchThrowable(() ->
            LabDatabases.migrate(dataSource, "classpath:labs/liquibase/checksum/v2.yaml")
        );

        assertThat(failure).isNotNull();
        assertThat(chainContains(failure, ValidationFailedException.class)).isTrue();
        assertThat(messages(failure)).containsIgnoringCase("checksum");
    }

    /**
     * Rollback cofa zmiany i usuwa dane wraz z tabelą.
     */
    @Test
    @DisplayName("Rollback cofa zmiany i usuwa dane wraz z tabelą")
    void shouldLoseTableDataWhenCreationIsRolledBack() throws Exception {
        DataSource dataSource = LabDatabases.dataSource(LabDatabases.memoryUrl("rollback"));
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (
                Liquibase liquibase = new Liquibase(
                    "labs/liquibase/rollback/master.yaml",
                    new ClassLoaderResourceAccessor(),
                    database
                )
            ) {
                liquibase.update("");
                jdbc.update("insert into lab_notes (text, priority) values ('kept?', 1)");

                liquibase.rollback(1, "");
                assertThat(columns(jdbc)).containsExactly("ID", "TEXT");
                assertThat(jdbc.queryForObject("select count(*) from lab_notes", Integer.class)).isEqualTo(1);

                liquibase.rollback(1, "");
                assertThat(
                    jdbc.queryForObject(
                        "select count(*) from information_schema.tables where table_name = 'LAB_NOTES'",
                        Integer.class
                    )
                ).isZero();
            }
        } finally {
            jdbc.execute("shutdown");
        }
    }

    /**
     * Walidacja Hibernate wykrywa brak migracji.
     * Argumenty "--klucz=wartość" mają najwyższy priorytet. SpringApplicationBuilder.properties(...)
     * ustawiłby tylko wartości DOMYŚLNE, które application.properties by nadpisało.
     * Liquibase przeszedł (schema bez kolumny version), ale Hibernate validate zatrzymał start.
     */
    @Test
    @DisplayName("Walidacja Hibernate wykrywa brak migracji")
    void shouldRejectSchemaWhenVersionMigrationIsMissing() {
        var rootLogger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(
            org.slf4j.Logger.ROOT_LOGGER_NAME
        );
        var previousLevel = rootLogger.getLevel();
        Throwable failure = catchThrowable(() -> {
            try {
                new SpringApplicationBuilder(ClinicShowcaseApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                        "--spring.datasource.url=" +
                        LabDatabases.memoryUrl("missing-version").replace("DB_CLOSE_DELAY=-1", "DB_CLOSE_DELAY=0"),
                        "--spring.liquibase.change-log=classpath:labs/liquibase/without-version/master.yaml",
                        "--spring.main.banner-mode=off",
                        "--logging.level.root=OFF"
                    )
                    .close();
            } finally {
                rootLogger.setLevel(previousLevel);
            }
        });

        assertThat(failure).isNotNull();
        assertThat(chainContains(failure, org.hibernate.tool.schema.spi.SchemaManagementException.class)).isTrue();
        assertThat(messages(failure)).contains("version");
    }

    private static List<String> columns(JdbcTemplate jdbc) {
        return jdbc.queryForList(
            "select column_name from information_schema.columns where table_name = 'LAB_NOTES' " +
            "order by ordinal_position",
            String.class
        );
    }

    private static boolean chainContains(Throwable failure, Class<?> type) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    private static String messages(Throwable failure) {
        StringBuilder builder = new StringBuilder();
        for (Throwable current = failure; current != null; current = current.getCause()) {
            builder.append(current.getMessage()).append('\n');
        }
        return builder.toString();
    }
}
