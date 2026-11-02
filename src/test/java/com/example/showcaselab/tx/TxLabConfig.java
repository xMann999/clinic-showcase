package com.example.showcaselab.tx;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Wspólna, izolowana konfiguracja laboratoriów transakcji: własna baza H2 w pamięci,
 * JdbcTemplate i DataSourceTransactionManager (obsługuje NESTED przez savepointy JDBC).
 * Bez JPA i bez beanów aplikacji. Pula nie jest ograniczona - REQUIRES_NEW potrzebuje
 * drugiego połączenia, gdy pierwsze jest zawieszone.
 */
@Configuration(proxyBeanMethods = false)
@EnableTransactionManagement
public class TxLabConfig {

    @Bean
    DataSource txLabDataSource() {
        return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
    }

    @Bean
    JdbcTemplate txLabJdbc(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table entries (name varchar(20) primary key)");
        return jdbc;
    }

    @Bean
    PlatformTransactionManager txLabTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate txLabTemplate(PlatformTransactionManager transactionManager) {
        return new TransactionTemplate(transactionManager);
    }

    public static List<String> entries(JdbcTemplate jdbc) {
        return jdbc.queryForList("select name from entries order by name", String.class);
    }
}
