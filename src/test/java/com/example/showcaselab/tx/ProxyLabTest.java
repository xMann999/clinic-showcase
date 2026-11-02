package com.example.showcaselab.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Proxy transakcyjne i pułapka self-invocation.
 * <h2>Cel</h2>
 * Wykazać znaczenie przejścia przez proxy dla granicy transakcji i końcowego stanu bazy.
 * <h2>Jak działa przykład</h2>
 * {@code @Transactional} działa, bo kontener wstrzykuje PROXY, które otwiera transakcję i woła obiekt
 * docelowy. Wywołanie this.metoda() wewnątrz klasy omija proxy - adnotacja jest ignorowana.
 * CGLIB tworzy PODKLASĘ: nie nadpisze metod final ani private, więc tam proxy też nie działa.
 * Interfejs + domyślne ustawienia @EnableTransactionManagement = proxy JDK (implementuje
 * interfejs, nie jest podklasą). Nie każdy bean jest proxy - tylko te z advice'ami.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Proxy powstaje tylko dla beanów objętych advice.</li>
 * <li>Wywołanie wewnętrzne omija proxy i nie cofa zapisu.</li>
 * <li>Osobny bean uruchamia proxy i cofa zapis.</li>
 * <li>Metoda final omija transakcyjne przechwycenie CGLIB.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test nie jest otoczony transakcją testową. Wyniki izolacji dotyczą H2; NESTED wymaga obsługi savepointów JDBC.
 */
@SpringJUnitConfig(
    {
        TxLabConfig.class,
        ProxyLabTest.SelfInvoking.class,
        ProxyLabTest.Writer.class,
        ProxyLabTest.Caller.class,
        ProxyLabTest.GreeterImpl.class,
        ProxyLabTest.PlainBean.class,
    }
)
@DisplayName("Proxy transakcyjne i pułapka self-invocation.")
class ProxyLabTest {

    static class SelfInvoking {

        private final JdbcTemplate jdbc;

        SelfInvoking(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        public void entryPoint() {
            this.transactionalWrite();
        }

        @Transactional
        public void transactionalWrite() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            jdbc.update("insert into entries(name) values ('self')");
            throw new IllegalStateException("after write");
        }

        @Transactional
        public final boolean finalMethodSeesTransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    /** Poprawka: operacja transakcyjna w osobnym beanie, wołana przez jego proxy. */
    static class Writer {

        private final JdbcTemplate jdbc;

        Writer(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Transactional
        public void transactionalWrite() {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            jdbc.update("insert into entries(name) values ('separate')");
            throw new IllegalStateException("after write");
        }
    }

    static class Caller {

        private final Writer writer;

        Caller(Writer writer) {
            this.writer = writer;
        }

        public void entryPoint() {
            writer.transactionalWrite();
        }
    }

    public interface Greeter {
        boolean greetInTransaction();
    }

    static class GreeterImpl implements Greeter {

        @Override
        @Transactional
        public boolean greetInTransaction() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    static class PlainBean {}

    @Autowired
    private SelfInvoking selfInvoking;

    @Autowired
    private Caller caller;

    @Autowired
    private Writer writer;

    @Autowired
    private Greeter greeter;

    @Autowired
    private PlainBean plainBean;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from entries");
    }

    /**
     * Proxy powstaje tylko dla beanów objętych advice.
     */
    @Test
    @DisplayName("Proxy powstaje tylko dla beanów objętych advice")
    void shouldCreateProxyWhenBeanIsAdvised() {
        assertThat(AopUtils.isCglibProxy(writer)).isTrue();
        assertThat(AopUtils.getTargetClass(writer)).isEqualTo(Writer.class);
        assertThat(AopUtils.isJdkDynamicProxy(greeter)).isTrue();
        assertThat(greeter.greetInTransaction()).isTrue();
        assertThat(AopUtils.isAopProxy(plainBean)).isFalse();
    }

    /**
     * Wywołanie wewnętrzne omija proxy i nie cofa zapisu.
     * Brak transakcji = auto-commit pojedynczego INSERT-a; wyjątek go nie cofnął.
     */
    @Test
    @DisplayName("Wywołanie wewnętrzne omija proxy i nie cofa zapisu")
    void shouldKeepWriteWhenSelfInvocationBypassesProxy() {
        assertThatThrownBy(selfInvoking::entryPoint).hasMessage("after write");

        assertThat(TxLabConfig.entries(jdbc)).containsExactly("self");
    }

    /**
     * Osobny bean uruchamia proxy i cofa zapis.
     */
    @Test
    @DisplayName("Osobny bean uruchamia proxy i cofa zapis")
    void shouldRollBackWriteWhenCallCrossesProxy() {
        assertThatThrownBy(caller::entryPoint).hasMessage("after write");
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * Metoda final omija transakcyjne przechwycenie CGLIB.
     */
    @Test
    @DisplayName("Metoda final omija transakcyjne przechwycenie CGLIB")
    void shouldSkipTransactionWhenMethodIsFinal() {
        assertThat(selfInvoking.finalMethodSeesTransaction()).isFalse();
    }
}
