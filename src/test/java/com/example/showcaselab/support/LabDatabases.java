package com.example.showcaselab.support;

import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Pomocnicze bazy dla laboratoriów: każda metoda tworzy NOWĄ, niezależną bazę H2,
 * więc laboratoria nie dzielą danych ani z aplikacją, ani między sobą.
 */
public final class LabDatabases {

    private LabDatabases() {}

    public static String memoryUrl(String name) {
        return "jdbc:h2:mem:" + name + "-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    public static DataSource dataSource(String url) {
        return new DriverManagerDataSource(url, "sa", "");
    }

    /** Uruchamia Liquibase tak jak Boot przy starcie aplikacji - ale na wskazanej bazie. */
    public static void migrate(DataSource dataSource, String changeLog) {
        String previousAnalytics = System.getProperty("liquibase.analytics.enabled");
        System.setProperty("liquibase.analytics.enabled", "false");
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        liquibase.setResourceLoader(new DefaultResourceLoader());
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException("Liquibase update failed", exception);
        } finally {
            if (previousAnalytics == null) {
                System.clearProperty("liquibase.analytics.enabled");
            } else {
                System.setProperty("liquibase.analytics.enabled", previousAnalytics);
            }
        }
    }

    public static final String APP_CHANGELOG = "classpath:db/changelog/db.changelog-master.yaml";
}
