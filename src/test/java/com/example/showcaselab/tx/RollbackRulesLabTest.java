package com.example.showcaselab.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Kiedy @Transactional cofa zmiany, a kiedy je zatwierdza.
 * <h2>Cel</h2>
 * Powiązać typ wyjątku, regułę rollbacku i obsługę lokalną z niezależnym odczytem po wywołaniu.
 * <h2>Jak działa przykład</h2>
 * Reguła domyślna Springa: RuntimeException i Error -> rollback; wyjątek SPRAWDZANY -> commit.
 * rollbackFor/noRollbackFor zmieniają regułę. setRollbackOnly() cofa bez wyjątku.
 * TransactionTemplate wyznacza transakcję na fragmencie metody, bez osobnego beana.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Wyjątek niesprawdzany cofa transakcję.</li>
 * <li>Wyjątek sprawdzany domyślnie zatwierdza transakcję.</li>
 * <li>RollbackFor cofa transakcję dla wyjątku sprawdzanego.</li>
 * <li>NoRollbackFor pozwala zatwierdzić mimo wyjątku.</li>
 * <li>RollbackOnly cofa zapis bez propagacji wyjątku.</li>
 * <li>Przechwycony wyjątek nie cofa zapisu automatycznie.</li>
 * <li>TransactionTemplate ogranicza rollback do wskazanego fragmentu.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test nie jest otoczony transakcją testową. Wyniki izolacji dotyczą H2; NESTED wymaga obsługi savepointów JDBC.
 */
@SpringJUnitConfig({ TxLabConfig.class, RollbackRulesLabTest.Writer.class })
@DisplayName("Kiedy @Transactional cofa zmiany, a kiedy je zatwierdza.")
class RollbackRulesLabTest {

    static class BusinessCheckedException extends Exception {

        BusinessCheckedException(String message) {
            super(message);
        }
    }

    @lombok.RequiredArgsConstructor
    static class Writer {

        private final JdbcTemplate jdbc;

        @Transactional
        public void runtimeFailure() {
            jdbc.update("insert into entries(name) values ('runtime')");
            throw new IllegalStateException("runtime");
        }

        @Transactional
        public void checkedFailure() throws BusinessCheckedException {
            jdbc.update("insert into entries(name) values ('checked')");
            throw new BusinessCheckedException("checked");
        }

        @Transactional(rollbackFor = BusinessCheckedException.class)
        public void checkedFailureWithRollbackFor() throws BusinessCheckedException {
            jdbc.update("insert into entries(name) values ('rollbackFor')");
            throw new BusinessCheckedException("checked");
        }

        @Transactional(noRollbackFor = IllegalArgumentException.class)
        public void runtimeFailureWithNoRollbackFor() {
            jdbc.update("insert into entries(name) values ('noRollback')");
            throw new IllegalArgumentException("tolerated");
        }

        /** Przechwycony wyjątek wymaga jawnego oznaczenia rollback-only, jeżeli zapis ma zostać cofnięty. */
        @Transactional
        public void caughtButMarkedRollbackOnly() {
            jdbc.update("insert into entries(name) values ('marked')");
            try {
                throw new IllegalStateException("handled locally");
            } catch (IllegalStateException handled) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        }

        /** Celowo połknięty wyjątek: proxy nie otrzyma błędu i zatwierdzi zapis. */
        @Transactional
        public void caughtAndIgnored() {
            jdbc.update("insert into entries(name) values ('ignored')");
            try {
                throw new IllegalStateException("swallowed");
            } catch (IllegalStateException swallowed) {}
        }
    }

    @Autowired
    private Writer writer;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clean() {
        jdbc.update("delete from entries");
    }

    /**
     * Wyjątek niesprawdzany cofa transakcję.
     */
    @Test
    @DisplayName("Wyjątek niesprawdzany cofa transakcję")
    void shouldRollBackWhenRuntimeExceptionEscapes() {
        assertThatThrownBy(writer::runtimeFailure).isInstanceOf(IllegalStateException.class);
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * Wyjątek sprawdzany domyślnie zatwierdza transakcję.
     */
    @Test
    @DisplayName("Wyjątek sprawdzany domyślnie zatwierdza transakcję")
    void shouldCommitWhenCheckedExceptionEscapes() {
        assertThatThrownBy(writer::checkedFailure).isInstanceOf(BusinessCheckedException.class);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("checked");
    }

    /**
     * RollbackFor cofa transakcję dla wyjątku sprawdzanego.
     */
    @Test
    @DisplayName("RollbackFor cofa transakcję dla wyjątku sprawdzanego")
    void shouldRollBackWhenCheckedExceptionMatchesRule() {
        assertThatThrownBy(writer::checkedFailureWithRollbackFor).isInstanceOf(BusinessCheckedException.class);
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * NoRollbackFor pozwala zatwierdzić mimo wyjątku.
     */
    @Test
    @DisplayName("NoRollbackFor pozwala zatwierdzić mimo wyjątku")
    void shouldCommitWhenRuntimeExceptionMatchesExclusion() {
        assertThatThrownBy(writer::runtimeFailureWithNoRollbackFor).isInstanceOf(IllegalArgumentException.class);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("noRollback");
    }

    /**
     * RollbackOnly cofa zapis bez propagacji wyjątku.
     */
    @Test
    @DisplayName("RollbackOnly cofa zapis bez propagacji wyjątku")
    void shouldRollBackWhenMarkedRollbackOnly() {
        writer.caughtButMarkedRollbackOnly();
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * Przechwycony wyjątek nie cofa zapisu automatycznie.
     */
    @Test
    @DisplayName("Przechwycony wyjątek nie cofa zapisu automatycznie")
    void shouldCommitWhenExceptionIsSwallowed() {
        writer.caughtAndIgnored();
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("ignored");
    }

    /**
     * TransactionTemplate ogranicza rollback do wskazanego fragmentu.
     */
    @Test
    @DisplayName("TransactionTemplate ogranicza rollback do wskazanego fragmentu")
    void shouldRollBackOnlyFragmentWhenTemplateFails() {
        jdbc.update("insert into entries(name) values ('before')");
        assertThatThrownBy(() ->
            transactionTemplate.executeWithoutResult(status -> {
                jdbc.update("insert into entries(name) values ('inside')");
                throw new IllegalStateException("fragment failed");
            })
        ).hasMessage("fragment failed");
        transactionTemplate.executeWithoutResult(status -> jdbc.update("insert into entries(name) values ('second')"));

        assertThat(TxLabConfig.entries(jdbc)).containsExactly("before", "second");
    }
}
