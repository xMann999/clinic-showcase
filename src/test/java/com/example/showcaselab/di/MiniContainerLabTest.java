package com.example.showcaselab.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Minimalny kontener DI napisany ręcznie - co Spring robi "pod spodem".
 * <h2>Cel</h2>
 * Zrozumieć rekurencyjne składanie grafu konstruktorów, cache instancji i wykrywanie błędów grafu.
 * <h2>Jak działa przykład</h2>
 * Mechanizm: dla żądanej klasy bierzemy JEDYNY konstruktor (refleksja), rekurencyjnie tworzymy
 * jego argumenty i zapamiętujemy instancję (singleton). Graf zależności powstaje z sygnatur
 * konstruktorów. Ograniczenia celowe: jeden konstruktor, brak interfejsów, zakresów, proxy,
 * kwalifikatorów i cyklu życia - to właśnie dokłada prawdziwy kontener.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Kontener buduje graf i współdzieli instancje.</li>
 * <li>Brak rejestracji zależności daje jawny błąd.</li>
 * <li>Cykl zależności jest wykrywany przed przepełnieniem stosu.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Małe konteksty nie skanują aplikacji. MiniContainer demonstruje tylko rekurencyjne składanie grafu, bez pełnego cyklu życia Springa.
 */
@DisplayName("Minimalny kontener DI napisany ręcznie - co Spring robi 'pod spodem'.")
class MiniContainerLabTest {

    static final class MiniContainer {

        private final Set<Class<?>> registered = new LinkedHashSet<>();
        private final Map<Class<?>, Object> singletons = new HashMap<>();
        private final Deque<Class<?>> creating = new ArrayDeque<>();

        MiniContainer register(Class<?>... types) {
            registered.addAll(Set.of(types));
            return this;
        }

        <T> T get(Class<T> type) {
            Object existing = singletons.get(type);
            if (existing != null) {
                return type.cast(existing);
            }
            if (!registered.contains(type)) {
                throw new IllegalStateException("No component registered for " + type.getSimpleName());
            }
            if (creating.contains(type)) {
                throw new IllegalStateException("Dependency cycle: " + creating + " -> " + type.getSimpleName());
            }
            creating.push(type);
            try {
                Constructor<?>[] constructors = type.getDeclaredConstructors();
                if (constructors.length != 1) {
                    throw new IllegalStateException(type.getSimpleName() + " must have exactly one constructor");
                }
                Constructor<?> constructor = constructors[0];
                Object[] arguments = java.util.Arrays.stream(constructor.getParameterTypes()).map(this::get).toArray();
                constructor.setAccessible(true);
                Object instance = constructor.newInstance(arguments);
                singletons.put(type, instance);
                return type.cast(instance);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Cannot create " + type.getSimpleName(), exception);
            } finally {
                creating.pop();
            }
        }
    }

    record SlotRepository() {}

    record SlotService(SlotRepository repository) {}

    record BookingService(SlotService slots, SlotRepository repository) {}

    record CycleA(CycleB b) {}

    record CycleB(CycleA a) {}

    /**
     * Kontener buduje graf i współdzieli instancje.
     */
    @Test
    @DisplayName("Kontener buduje graf i współdzieli instancje")
    void shouldShareDependenciesWhenBuildingGraph() {
        MiniContainer container = new MiniContainer()
            .register(SlotRepository.class, SlotService.class, BookingService.class);

        BookingService booking = container.get(BookingService.class);

        assertThat(booking.slots().repository()).isSameAs(booking.repository());
        assertThat(container.get(BookingService.class)).isSameAs(booking);
    }

    /**
     * Brak rejestracji zależności daje jawny błąd.
     */
    @Test
    @DisplayName("Brak rejestracji zależności daje jawny błąd")
    void shouldRejectLookupWhenDependencyIsUnregistered() {
        MiniContainer container = new MiniContainer().register(SlotService.class);

        assertThatThrownBy(() -> container.get(SlotService.class)).hasMessageContaining(
            "No component registered for SlotRepository"
        );
    }

    /**
     * Cykl zależności jest wykrywany przed przepełnieniem stosu.
     */
    @Test
    @DisplayName("Cykl zależności jest wykrywany przed przepełnieniem stosu")
    void shouldRejectCycleWhenConstructorsFormLoop() {
        MiniContainer container = new MiniContainer().register(CycleA.class, CycleB.class);

        assertThatThrownBy(() -> container.get(CycleA.class)).hasMessageContaining("Dependency cycle");
    }
}
