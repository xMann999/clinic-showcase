package com.example.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.command.VisitCreateCommand;
import com.example.clinic.error.DomainException;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Dwa równoległe utworzenia nakładających się slotów u doktora BEZ żadnych wizyt.
 *
 * Uruchomienie: ./mvnw -Dit.test=SlotCreationConcurrencyIT verify
 *
 * Wątek 1 tworzy slot w transakcji, która po INSERT-cie jeszcze trwa (trzyma blokadę wiersza
 * doktora). Wątek 2 wywołuje VisitService.createSlot i czeka na SELECT ... FOR UPDATE.
 * Test potwierdza w bazie, że sesja wątku 2 jest zablokowana (INFORMATION_SCHEMA.SESSIONS),
 * dopiero wtedy zwalnia wątek 1. Po commit wątek 2 czyta aktualne sloty i dostaje VISIT_OVERLAP.
 * Bariera "oba wątki dotarły" nie stoi ZA blokadą - inaczej test by się zakleszczył.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class SlotCreationConcurrencyIT {

    private static final LocalDateTime TEN = FixedClockConfiguration.TOMORROW_9AM.withHour(10);

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private VisitService visitService;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void overlappingSlotsCreatedConcurrently_secondWaitsForLockAndIsRejected() throws Exception {
        Long doctorId = fixtures.doctor();
        CountDownLatch firstInserted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() ->
                outcome(() ->
                    fixtures
                        .transactionTemplate()
                        .executeWithoutResult(status -> {
                            visitService.createSlot(new VisitCreateCommand(doctorId, TEN, 60));
                            firstInserted.countDown();
                            awaitLatch(releaseFirst);
                        })
                )
            );
            assertThat(firstInserted.await(10, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() ->
                outcome(() -> visitService.createSlot(new VisitCreateCommand(doctorId, TEN.plusMinutes(30), 60)))
            );
            assertThat(waitUntilSomeSessionIsBlocked(Duration.ofSeconds(10)))
                .as("second transaction should wait for the doctor row lock")
                .isTrue();
            releaseFirst.countDown();

            assertThat(first.get(30, TimeUnit.SECONDS)).isEqualTo("OK");
            assertThat(second.get(30, TimeUnit.SECONDS)).isEqualTo("VISIT_OVERLAP");
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(visitRepository.countByDoctorId(doctorId)).isEqualTo(1);
    }

    @Test
    void touchingSlotsCreatedOneAfterAnother_bothAccepted() {
        Long doctorId = fixtures.doctor();
        visitService.createSlot(new VisitCreateCommand(doctorId, TEN, 30));
        visitService.createSlot(new VisitCreateCommand(doctorId, TEN.plusMinutes(30), 30));

        assertThat(visitRepository.countByDoctorId(doctorId)).isEqualTo(2);
    }

    private static String outcome(Runnable action) {
        try {
            action.run();
            return "OK";
        } catch (DomainException exception) {
            return exception.code().name();
        }
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(20, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Latch not released");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Obserwacja stanu bazy (nie sterowanie kolejnością): H2 zapisuje w BLOCKER_ID sesję,
     * na którą czeka zablokowana transakcja. Krótkie parkowanie między odczytami tylko
     * odciąża procesor; o kolejności decydują latch i blokada wiersza.
     */
    private boolean waitUntilSomeSessionIsBlocked(Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.sessions where blocker_id is not null",
                Integer.class
            );
            if (blocked != null && blocked > 0) {
                return true;
            }
            LockSupport.parkNanos(Duration.ofMillis(20).toNanos());
        }
        return false;
    }
}
