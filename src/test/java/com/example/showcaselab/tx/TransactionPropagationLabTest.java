package com.example.showcaselab.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Wszystkie siedem propagacji @Transactional.
 * <h2>Cel</h2>
 * Porównać współdzieloną transakcję, niezależny commit i savepoint oraz wymagania pozostałych propagacji.
 * <h2>Jak działa przykład</h2>
 * Metoda zewnętrzna (Outer) zapisuje A i woła metodę wewnętrzną (Inner) w INNYM beanie -
 * tylko wtedy wywołanie przechodzi przez proxy i propagacja w ogóle działa.
 * Propagacja decyduje, CZY i KTÓRĄ transakcję użyć; izolacja (IsolationLabTest) - co transakcja
 * widzi z cudzych zmian. To dwa niezależne ustawienia.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>REQUIRED cofa całość po propagacji błędu.</li>
 * <li>Przechwycenie błędu REQUIRED nie usuwa rollback-only.</li>
 * <li>REQUIRED współdzieli zatwierdzenie.</li>
 * <li>Propagacja błędu REQUIRES_NEW cofa obie transakcje.</li>
 * <li>Przechwycony błąd REQUIRES_NEW pozwala zapisać zewnętrzną.</li>
 * <li>REQUIRES_NEW zatwierdza obie transakcje niezależnie.</li>
 * <li>Zatwierdzona REQUIRES_NEW przetrwa błąd zewnętrznej.</li>
 * <li>NESTED cofa do savepointu i pozwala zatwierdzić zewnętrzną.</li>
 * <li>Błąd zewnętrzny cofa także udaną część NESTED.</li>
 * <li>SUPPORTS korzysta z transakcji tylko gdy istnieje.</li>
 * <li>NOT_SUPPORTED zawiesza istniejącą transakcję.</li>
 * <li>MANDATORY wymaga istniejącej transakcji.</li>
 * <li>NEVER odrzuca istniejącą transakcję.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test nie jest otoczony transakcją testową. Wyniki izolacji dotyczą H2; NESTED wymaga obsługi savepointów JDBC.
 */
@SpringJUnitConfig(
    { TxLabConfig.class, TransactionPropagationLabTest.Outer.class, TransactionPropagationLabTest.Inner.class }
)
@DisplayName("Wszystkie siedem propagacji @Transactional.")
class TransactionPropagationLabTest {

    @lombok.RequiredArgsConstructor
    static class Inner {

        private final JdbcTemplate jdbc;

        private void write(String name, boolean fail) {
            jdbc.update("insert into entries(name) values (?)", name);
            if (fail) {
                throw new IllegalStateException("inner failure");
            }
        }

        @Transactional(propagation = Propagation.REQUIRED)
        public void required(String name, boolean fail) {
            write(name, fail);
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void requiresNew(String name, boolean fail) {
            write(name, fail);
        }

        @Transactional(propagation = Propagation.NESTED)
        public void nested(String name, boolean fail) {
            write(name, fail);
        }

        @Transactional(propagation = Propagation.SUPPORTS)
        public boolean supports() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Transactional(propagation = Propagation.NOT_SUPPORTED)
        public boolean notSupported() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Transactional(propagation = Propagation.MANDATORY)
        public boolean mandatory() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }

        @Transactional(propagation = Propagation.NEVER)
        public boolean never() {
            return TransactionSynchronizationManager.isActualTransactionActive();
        }
    }

    @lombok.RequiredArgsConstructor
    static class Outer {

        private final JdbcTemplate jdbc;

        /** Przechwycenie błędu nie usuwa znacznika rollback-only ustawionego przez wewnętrzne proxy. */
        @Transactional
        public void writeAThen(Runnable innerCall, boolean catchInner, boolean failAfterInner) {
            jdbc.update("insert into entries(name) values ('A')");
            if (catchInner) {
                try {
                    innerCall.run();
                } catch (IllegalStateException ignored) {}
            } else {
                innerCall.run();
            }
            if (failAfterInner) {
                throw new IllegalStateException("outer failure");
            }
        }

        @Transactional
        public <T> T insideTransaction(Supplier<T> call) {
            return call.get();
        }
    }

    @Autowired
    private Outer outer;

    @Autowired
    private Inner inner;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("delete from entries");
    }

    /**
     * REQUIRED cofa całość po propagacji błędu.
     */
    @Test
    @DisplayName("REQUIRED cofa całość po propagacji błędu")
    void shouldRollBackAllWhenRequiredFailureEscapes() {
        assertThatThrownBy(() -> outer.writeAThen(() -> inner.required("B", true), false, false)).isInstanceOf(
            IllegalStateException.class
        );
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * Przechwycenie błędu REQUIRED nie usuwa rollback-only.
     * Inner (ta sama transakcja) oznaczyła ją jako rollback-only. Outer złapał wyjątek
     * i próbuje commit - menedżer cofa wszystko i zgłasza UnexpectedRollbackException.
     */
    @Test
    @DisplayName("Przechwycenie błędu REQUIRED nie usuwa rollback-only")
    void shouldRejectCommitWhenRequiredMarksRollbackOnly() {
        assertThatThrownBy(() -> outer.writeAThen(() -> inner.required("B", true), true, false)).isInstanceOf(
            UnexpectedRollbackException.class
        );
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * REQUIRED współdzieli zatwierdzenie.
     */
    @Test
    @DisplayName("REQUIRED współdzieli zatwierdzenie")
    void shouldShareCommitWhenRequiredSucceeds() {
        outer.writeAThen(() -> inner.required("B", false), false, false);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("A", "B");
    }

    /**
     * Propagacja błędu REQUIRES_NEW cofa obie transakcje.
     */
    @Test
    @DisplayName("Propagacja błędu REQUIRES_NEW cofa obie transakcje")
    void shouldRollBackBothWhenRequiresNewFailureEscapes() {
        assertThatThrownBy(() -> outer.writeAThen(() -> inner.requiresNew("B", true), false, false)).isInstanceOf(
            IllegalStateException.class
        );
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * Przechwycony błąd REQUIRES_NEW pozwala zapisać zewnętrzną.
     */
    @Test
    @DisplayName("Przechwycony błąd REQUIRES_NEW pozwala zapisać zewnętrzną")
    void shouldCommitOuterWhenRequiresNewFailureIsCaught() {
        outer.writeAThen(() -> inner.requiresNew("B", true), true, false);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("A");
    }

    /**
     * REQUIRES_NEW zatwierdza obie transakcje niezależnie.
     */
    @Test
    @DisplayName("REQUIRES_NEW zatwierdza obie transakcje niezależnie")
    void shouldCommitIndependentlyWhenRequiresNewSucceeds() {
        outer.writeAThen(() -> inner.requiresNew("B", false), false, false);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("A", "B");
    }

    /**
     * Zatwierdzona REQUIRES_NEW przetrwa błąd zewnętrznej.
     */
    @Test
    @DisplayName("Zatwierdzona REQUIRES_NEW przetrwa błąd zewnętrznej")
    void shouldKeepInnerCommitWhenOuterFails() {
        assertThatThrownBy(() -> outer.writeAThen(() -> inner.requiresNew("B", false), false, true)).hasMessage(
            "outer failure"
        );
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("B");
    }

    /**
     * NESTED cofa do savepointu i pozwala zatwierdzić zewnętrzną.
     */
    @Test
    @DisplayName("NESTED cofa do savepointu i pozwala zatwierdzić zewnętrzną")
    void shouldRollBackToSavepointWhenNestedFailureIsCaught() {
        outer.writeAThen(() -> inner.nested("B", true), true, false);
        assertThat(TxLabConfig.entries(jdbc)).containsExactly("A");
    }

    /**
     * Błąd zewnętrzny cofa także udaną część NESTED.
     * W odróżnieniu od REQUIRES_NEW zagnieżdżona część nie ma własnego commit.
     */
    @Test
    @DisplayName("Błąd zewnętrzny cofa także udaną część NESTED")
    void shouldRollBackNestedWorkWhenOuterFails() {
        assertThatThrownBy(() -> outer.writeAThen(() -> inner.nested("B", false), false, true)).hasMessage(
            "outer failure"
        );
        assertThat(TxLabConfig.entries(jdbc)).isEmpty();
    }

    /**
     * SUPPORTS korzysta z transakcji tylko gdy istnieje.
     */
    @Test
    @DisplayName("SUPPORTS korzysta z transakcji tylko gdy istnieje")
    void shouldJoinTransactionWhenSupportsFindsOne() {
        assertThat(inner.supports()).isFalse();
        assertThat(outer.insideTransaction(inner::supports)).isTrue();
    }

    /**
     * NOT_SUPPORTED zawiesza istniejącą transakcję.
     */
    @Test
    @DisplayName("NOT_SUPPORTED zawiesza istniejącą transakcję")
    void shouldSuspendTransactionWhenNotSupportedIsCalled() {
        assertThat(outer.insideTransaction(inner::notSupported)).isFalse();
    }

    /**
     * MANDATORY wymaga istniejącej transakcji.
     */
    @Test
    @DisplayName("MANDATORY wymaga istniejącej transakcji")
    void shouldRejectCallWhenMandatoryFindsNoTransaction() {
        assertThatThrownBy(inner::mandatory).isInstanceOf(IllegalTransactionStateException.class);
        assertThat(outer.insideTransaction(inner::mandatory)).isTrue();
    }

    /**
     * NEVER odrzuca istniejącą transakcję.
     */
    @Test
    @DisplayName("NEVER odrzuca istniejącą transakcję")
    void shouldRejectCallWhenNeverFindsTransaction() {
        assertThat(inner.never()).isFalse();
        assertThatThrownBy(() -> outer.insideTransaction(inner::never)).isInstanceOf(
            IllegalTransactionStateException.class
        );
    }
}
