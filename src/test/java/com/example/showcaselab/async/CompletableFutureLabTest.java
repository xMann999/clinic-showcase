package com.example.showcaselab.async;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * CompletableFuture na dwóch lokalnych źródłach danych (bez sieci).
 * <h2>Cel</h2>
 * Łączenie i spłaszczanie wyników, rozróżnianie obsługi błędu od obserwacji oraz granice timeoutu.
 * <h2>Jak działa przykład</h2>
 * Zasady w tym pliku:
 * - własny, skończony executor z nazwanymi wątkami, zamykany po teście (common pool jest
 *   współdzielony przez całą JVM i nie jest naszą własnością);
 * - kolejność i "równoległość" sterowana latchami, nie sleep ani pomiarem milisekund;
 * - ograniczone czasowo get() w punkcie zbierania wyników;
 * - przez wątki przekazujemy DANE (rekordy), nigdy zarządzane encje ani EntityManager -
 *   persistence context i transakcja są przypięte do wątku.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Dwa źródła łączą wyniki po wspólnym starcie.</li>
 * <li>thenCompose spłaszcza przyszły wynik.</li>
 * <li>Kolejność odbioru odpowiada kolejności listy zadań.</li>
 * <li>Obsługa błędu różni się od obserwacji zakończenia.</li>
 * <li>Timeout kopii nie przerywa zadania źródłowego.</li>
 * <li>Przekazany MDC jest sprzątany na tym samym wątku.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Przykład nie mierzy szybkości ani nie używa sieci. Timeout wyniku nie jest anulowaniem pracy.
 */
@DisplayName("CompletableFuture na dwóch lokalnych źródłach danych (bez sieci).")
class CompletableFutureLabTest {

    record Doctor(long id, String name) {}

    record Slots(long doctorId, int free) {}

    record Summary(String doctor, int freeSlots) {}

    private ExecutorService executor;
    private Map<String, String> previousMdc;
    private final Set<String> threadNames = ConcurrentHashMap.newKeySet();

    @BeforeEach
    void createExecutor() {
        previousMdc = MDC.getCopyOfContextMap();
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newFixedThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "lab-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @AfterEach
    void shutdownExecutor() throws InterruptedException {
        executor.shutdownNow();
        try {
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            if (previousMdc == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previousMdc);
            }
        }
    }

    private <T> Supplier<T> recordingThread(Supplier<T> supplier) {
        return () -> {
            threadNames.add(Thread.currentThread().getName());
            return supplier.get();
        };
    }

    /**
     * Dwa źródła łączą wyniki po wspólnym starcie.
     * Każde źródło czeka, aż drugie też wystartuje: test kończy się tylko wtedy, gdy
     * oba zadania naprawdę działają w tym samym czasie na dwóch wątkach.
     */
    @Test
    @DisplayName("Dwa źródła łączą wyniki po wspólnym starcie")
    void shouldCombineResultsWhenSourcesRunConcurrently() throws Exception {
        CountDownLatch bothStarted = new CountDownLatch(2);
        CompletableFuture<Doctor> doctor = CompletableFuture.supplyAsync(
            recordingThread(() -> {
                awaitBoth(bothStarted);
                return new Doctor(1, "Jan Kowalski");
            }),
            executor
        );
        CompletableFuture<Slots> slots = CompletableFuture.supplyAsync(
            recordingThread(() -> {
                awaitBoth(bothStarted);
                return new Slots(1, 4);
            }),
            executor
        );

        Summary summary = doctor.thenCombine(slots, (d, s) -> new Summary(d.name(), s.free())).get(5, TimeUnit.SECONDS);

        assertThat(summary).isEqualTo(new Summary("Jan Kowalski", 4));
        assertThat(threadNames).allMatch(name -> name.startsWith("lab-worker-")).hasSize(2);
    }

    /**
     * thenCompose spłaszcza przyszły wynik.
     */
    @Test
    @DisplayName("thenCompose spłaszcza przyszły wynik")
    void shouldFlattenFutureWhenUsingThenCompose() throws Exception {
        CompletableFuture<Doctor> doctor = CompletableFuture.supplyAsync(() -> new Doctor(2, "Ola Nowak"), executor);

        CompletableFuture<String> upper = doctor.thenApply(d -> d.name().toUpperCase(java.util.Locale.ROOT));
        CompletableFuture<CompletableFuture<Slots>> nested = doctor.thenApply(this::loadSlots);
        CompletableFuture<Slots> flat = doctor.thenCompose(this::loadSlots);

        assertThat(upper.get(5, TimeUnit.SECONDS)).isEqualTo("OLA NOWAK");
        CompletableFuture<Slots> inner = nested.get(5, TimeUnit.SECONDS);
        assertThat(inner.get(5, TimeUnit.SECONDS)).isEqualTo(new Slots(2, 2));
        assertThat(flat.get(5, TimeUnit.SECONDS)).isEqualTo(new Slots(2, 2));
    }

    private CompletableFuture<Slots> loadSlots(Doctor doctor) {
        return CompletableFuture.supplyAsync(() -> new Slots(doctor.id(), (int) doctor.id()), executor);
    }

    /**
     * Kolejność odbioru odpowiada kolejności listy zadań.
     */
    @Test
    @DisplayName("Kolejność odbioru odpowiada kolejności listy zadań")
    void shouldPreserveSubmissionOrderWhenCollectingResults() throws Exception {
        Slots sequential = loadSlots(new Doctor(3, "A")).get(5, TimeUnit.SECONDS);
        List<CompletableFuture<Slots>> tasks = List.of(loadSlots(new Doctor(3, "A")), loadSlots(new Doctor(4, "B")));
        List<Slots> parallel = new java.util.ArrayList<>();
        for (CompletableFuture<Slots> task : tasks) {
            parallel.add(task.get(5, TimeUnit.SECONDS));
        }

        assertThat(sequential.free()).isEqualTo(3);
        assertThat(parallel).extracting(Slots::free).containsExactly(3, 4);
    }

    record Availability(boolean available, List<Slots> slots) {}

    /** Błąd źródła daje jawny stan niedostępności, a nie pozorny sukces z pustą listą. */
    @Test
    @DisplayName("exceptionally zamienia błąd na jawny stan niedostępności")
    void shouldMarkUnavailableWhenSourceFails() throws Exception {
        CompletableFuture<List<Slots>> source = failedSource();
        Availability result = source
            .thenApply(slots -> new Availability(true, slots))
            .exceptionally(error -> new Availability(false, List.of()))
            .get(5, TimeUnit.SECONDS);

        assertThat(result).isEqualTo(new Availability(false, List.of()));
    }

    /** Ta sama funkcja handle otrzymuje wynik sukcesu albo przyczynę błędu. */
    @Test
    @DisplayName("handle rozróżnia sukces i błąd")
    void shouldHandleBothOutcomesWhenSourceCompletes() throws Exception {
        var success = CompletableFuture.completedFuture(List.of(new Slots(1, 2)));
        var failure = failedSource();

        assertThat(
            success.handle((value, error) -> error == null ? value.size() : -1).get(5, TimeUnit.SECONDS)
        ).isEqualTo(1);
        assertThat(
            failure.handle((value, error) -> error == null ? value.size() : -1).get(5, TimeUnit.SECONDS)
        ).isEqualTo(-1);
    }

    /** Obserwator zostaje wywołany raz, lecz wynik zależnego etapu nadal zawiera błąd źródła. */
    @Test
    @DisplayName("whenComplete obserwuje błąd bez jego usunięcia")
    void shouldKeepFailureWhenCompletionIsObserved() {
        AtomicInteger observations = new AtomicInteger();
        var observed = failedSource().whenComplete((value, error) -> observations.incrementAndGet());

        assertThatThrownBy(() -> observed.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("schedule source down");
        assertThat(observations).hasValue(1);
    }

    private CompletableFuture<List<Slots>> failedSource() {
        return CompletableFuture.supplyAsync(
            () -> {
                throw new IllegalStateException("schedule source down");
            },
            executor
        );
    }

    /** Niedokończony składnik utrzymuje allOf w oczekiwaniu; wyniki odbieramy z obu źródeł. */
    @Test
    @DisplayName("allOf czeka na wszystkie składniki")
    void shouldWaitForEverySourceWhenUsingAllOf() throws Exception {
        var first = CompletableFuture.completedFuture("first");
        var second = new CompletableFuture<String>();
        var combined = CompletableFuture.allOf(first, second);

        assertThat(combined.isDone()).isFalse();
        second.complete("second");

        assertThat(combined.get(5, TimeUnit.SECONDS)).isNull();
        assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("first");
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
    }

    /** Pierwsze zakończenie może być błędem. Drugie źródło kończymy i odbieramy osobno. */
    @Test
    @DisplayName("anyOf przekazuje również pierwsze błędne zakończenie")
    void shouldPropagateFailureWhenAnySourceFailsFirst() throws Exception {
        var first = new CompletableFuture<String>();
        var second = new CompletableFuture<String>();
        var any = CompletableFuture.anyOf(first, second);

        assertThat(any.isDone()).isFalse();
        first.completeExceptionally(new IllegalStateException("boom"));
        assertThatThrownBy(() -> any.get(5, TimeUnit.SECONDS))
            .isInstanceOf(ExecutionException.class)
            .hasRootCauseMessage("boom");
        second.complete("second");
        assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo("second");
    }

    /** Latch utrzymuje pracę źródła. Oba timeouty dotyczą kopii; finally zawsze zwalnia źródło. */
    @Test
    @DisplayName("Timeout kopii nie przerywa pracy źródła")
    void shouldKeepSourceRunningWhenCopiesTimeOut() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        var source = CompletableFuture.supplyAsync(
            () -> {
                awaitQuietly(release);
                return "late";
            },
            executor
        );
        try {
            assertThat(
                source.copy().completeOnTimeout("default", 50, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS)
            ).isEqualTo("default");
            assertThatThrownBy(() -> source.copy().orTimeout(50, TimeUnit.MILLISECONDS).get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
            assertThat(source.isDone()).isFalse();
        } finally {
            release.countDown();
            assertThat(source.get(5, TimeUnit.SECONDS)).isEqualTo("late");
        }
    }

    /**
     * Przekazany MDC jest sprzątany na tym samym wątku.
     * Wątek wraca do puli - przywracamy poprzedni stan, by nie "przeciekł" do innego zadania.
     */
    @Test
    @DisplayName("Przekazany MDC jest sprzątany na tym samym wątku")
    void shouldRestoreContextWhenMdcIsTransferred() throws Exception {
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        executor = Executors.newSingleThreadExecutor();
        MDC.put("requestId", "req-123");
        Map<String, String> captured = MDC.getCopyOfContextMap();

        String withoutTransfer = CompletableFuture.supplyAsync(
            () -> String.valueOf(MDC.get("requestId")),
            executor
        ).get(5, TimeUnit.SECONDS);
        String withTransfer = CompletableFuture.supplyAsync(
            () -> {
                Map<String, String> previous = MDC.getCopyOfContextMap();
                MDC.setContextMap(captured);
                try {
                    return MDC.get("requestId");
                } finally {
                    if (previous == null) {
                        MDC.clear();
                    } else {
                        MDC.setContextMap(previous);
                    }
                }
            },
            executor
        ).get(5, TimeUnit.SECONDS);
        String afterCleanup = CompletableFuture.supplyAsync(() -> String.valueOf(MDC.get("requestId")), executor).get(
            5,
            TimeUnit.SECONDS
        );

        assertThat(withoutTransfer).isEqualTo("null");
        assertThat(withTransfer).isEqualTo("req-123");
        assertThat(afterCleanup).isEqualTo("null");
    }

    private static void awaitBoth(CountDownLatch latch) {
        latch.countDown();
        awaitQuietly(latch);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
