package com.example.showcaselab.tx;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Anomalie izolacji na DWÓCH niezależnych połączeniach JDBC (T1, T2).
 * <h2>Cel</h2>
 * Obserwować widoczność zmian między dwiema transakcjami JDBC przy jawnej kolejności operacji.
 * <h2>Jak działa przykład</h2>
 * Opis i tabela gwarancji: docs/labs/transactions.md
 * H2 używa MVCC: odczyt nie czeka na zapis innej transakcji, więc kolejne kroki T1/T2 można
 * wykonać po kolei w jednym wątku - kolejność jest jawna w kodzie, bez sleep i bez wątków.
 * Hibernate i jego cache pierwszego poziomu nie biorą udziału - to czyste zachowanie bazy.
 * Wyniki dotyczą H2; PostgreSQL czy MySQL mogą zachowywać się inaczej na tym samym poziomie.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>READ COMMITTED nie widzi niezatwierdzonej zmiany.</li>
 * <li>READ COMMITTED dopuszcza niepowtarzalny odczyt.</li>
 * <li>REPEATABLE READ zachowuje odczytaną wartość do końca transakcji.</li>
 * <li>READ COMMITTED widzi nowe wiersze po cudzym commit.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test nie jest otoczony transakcją testową. Wyniki izolacji dotyczą H2; NESTED wymaga obsługi savepointów JDBC.
 */
@DisplayName("Anomalie izolacji na DWÓCH niezależnych połączeniach JDBC (T1, T2).")
class IsolationLabTest {

    private String url;
    private Connection setup;
    private Connection t1;
    private Connection t2;

    @BeforeEach
    void open() throws SQLException {
        url = "jdbc:h2:mem:iso-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=0;LOCK_TIMEOUT=2000";
        setup = DriverManager.getConnection(url, "sa", "");
        try (Statement statement = setup.createStatement()) {
            statement.execute("create table slots (id int primary key, doctor varchar(10), minutes int)");
            statement.execute("insert into slots values (1, 'A', 30)");
        }
        t1 = DriverManager.getConnection(url, "sa", "");
        t2 = DriverManager.getConnection(url, "sa", "");
        t1.setAutoCommit(false);
        t2.setAutoCommit(false);
    }

    @AfterEach
    void close() throws SQLException {
        SQLException failure = null;
        for (Connection connection : new Connection[] { t1, t2, setup }) {
            if (connection == null) {
                continue;
            }
            try (connection) {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (SQLException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * READ COMMITTED nie widzi niezatwierdzonej zmiany.
     * T2 nie zatwierdziła zmiany - T1 nie może jej zobaczyć (T2 może jeszcze zrobić rollback).
     */
    @Test
    @DisplayName("READ COMMITTED nie widzi niezatwierdzonej zmiany")
    void shouldHideUncommittedWriteWhenUsingReadCommitted() throws SQLException {
        t1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        update(t2, "update slots set minutes = 60 where id = 1");

        assertThat(minutes(t1)).isEqualTo(30);
        t2.rollback();
        assertThat(minutes(t1)).isEqualTo(30);
    }

    /**
     * READ COMMITTED dopuszcza niepowtarzalny odczyt.
     */
    @Test
    @DisplayName("READ COMMITTED dopuszcza niepowtarzalny odczyt")
    void shouldSeeNewValueWhenOtherTransactionCommits() throws SQLException {
        t1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        int first = minutes(t1);
        update(t2, "update slots set minutes = 60 where id = 1");
        t2.commit();
        int second = minutes(t1);

        assertThat(first).isEqualTo(30);
        assertThat(second).isEqualTo(60);
    }

    /**
     * REPEATABLE READ zachowuje odczytaną wartość do końca transakcji.
     * Nowa transakcja widzi już zatwierdzoną zmianę.
     */
    @Test
    @DisplayName("REPEATABLE READ zachowuje odczytaną wartość do końca transakcji")
    void shouldKeepSnapshotWhenUsingRepeatableRead() throws SQLException {
        t1.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        int first = minutes(t1);
        update(t2, "update slots set minutes = 60 where id = 1");
        t2.commit();

        assertThat(minutes(t1)).isEqualTo(first).isEqualTo(30);
        t1.commit();

        assertThat(minutes(t1)).isEqualTo(60);
    }

    /**
     * READ COMMITTED widzi nowe wiersze po cudzym commit.
     */
    @Test
    @DisplayName("READ COMMITTED widzi nowe wiersze po cudzym commit")
    void shouldSeeNewRowsWhenUsingReadCommitted() throws SQLException {
        t1.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        int before = count(t1);
        update(t2, "insert into slots values (2, 'A', 15)");
        t2.commit();
        int after = count(t1);

        assertThat(before).isEqualTo(1);
        assertThat(after).isEqualTo(2);
    }

    private static void update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static int minutes(Connection connection) throws SQLException {
        try (
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("select minutes from slots where id = 1")
        ) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int count(Connection connection) throws SQLException {
        try (
            Statement statement = connection.createStatement();
            ResultSet rs = statement.executeQuery("select count(*) from slots where doctor = 'A'")
        ) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
