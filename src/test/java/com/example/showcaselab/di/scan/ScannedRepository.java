package com.example.showcaselab.di.scan;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@code @Repository} na RĘCZNIE napisanej klasie dostępu do danych: oprócz rejestracji beana pozwala
 * PersistenceExceptionTranslationPostProcessor tłumaczyć wyjątki technologii (np. JPA) na
 * DataAccessException. Na interfejsie Spring Data (JpaRepository) adnotacja jest zbędna -
 * Spring Data rejestruje i tłumaczy sam.
 */
@Repository
public class ScannedRepository {

    private JdbcTemplate jdbcTemplate;

    public void useJdbc(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean configured() {
        return jdbcTemplate != null;
    }
}
