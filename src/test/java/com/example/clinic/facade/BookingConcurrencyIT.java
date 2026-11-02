package com.example.clinic.facade;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Visit;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Dwie równoległe rezerwacje TEJ SAMEJ wizyty - ochrona przez @Version.
 *
 * Uruchomienie: ./mvnw -Dit.test=BookingConcurrencyIT verify
 *
 * Każdy wątek ma własną transakcję (i własne połączenie). Najpierw obie transakcje czytają
 * wizytę w wersji 0 (bariera gwarantuje, że oba odczyty nastąpiły przed jakimkolwiek zapisem),
 * potem obie przechodzą przez proxy fasady i próbują zatwierdzić. UPDATE ... WHERE version = 0
 * uda się tylko raz; drugi commit dotyka 0 wierszy i kończy się konfliktem.
 * Test nie ma jednej zewnętrznej transakcji, która ukryłaby commit, i nie używa sleep.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class BookingConcurrencyIT {

    @Autowired
    private TestFixtures fixtures;

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void twoBookingsReadingSameVersion_exactlyOneCommits() throws Exception {
        Long visitId = fixtures.visit(fixtures.doctor(), FixedClockConfiguration.TOMORROW_9AM, 30);
        Long userA = fixtures.user();
        Long patientA = fixtures.patient(userA);
        Long userB = fixtures.user();
        Long patientB = fixtures.patient(userB);
        CyclicBarrier bothHaveRead = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<String> first = executor.submit(() -> attempt(userA, patientA, visitId, bothHaveRead));
            Future<String> second = executor.submit(() -> attempt(userB, patientB, visitId, bothHaveRead));

            List<String> outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));

            assertThat(outcomes).containsExactlyInAnyOrder("COMMITTED", "OPTIMISTIC_CONFLICT");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        Visit finalState = new TransactionTemplate(transactionManager).execute(status ->
            visitRepository.findById(visitId).orElseThrow()
        );
        Long bookedPatient = finalState.findPatient().map(Patient::getId).orElseThrow();
        assertThat(bookedPatient).isIn(patientA, patientB);
        assertThat(finalState.getVersion()).isEqualTo(1);
    }

    private String attempt(Long userId, Long patientId, Long visitId, CyclicBarrier bothHaveRead) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        try {
            transaction.executeWithoutResult(status -> {
                // Odczyt do persistence context tej transakcji; fasada (REQUIRED) dostanie ten sam obiekt.
                visitRepository.findById(visitId).orElseThrow();
                await(bothHaveRead);
                bookingFacade.book(userId, visitId, patientId);
            });
            return "COMMITTED";
        } catch (OptimisticLockingFailureException conflict) {
            return "OPTIMISTIC_CONFLICT";
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Barrier not reached", exception);
        }
    }
}
