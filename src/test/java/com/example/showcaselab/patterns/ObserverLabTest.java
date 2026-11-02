package com.example.showcaselab.patterns;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;

/**
 * Wzorzec Observer przez zdarzenia Springa.
 * <h2>Cel</h2>
 * Wykazać rozdzielenie nadawcy zdarzenia od wielu odbiorców rejestrowanych przez kontener.
 * <h2>Jak działa przykład</h2>
 * Nadawca publikuje zdarzenie i nie zna odbiorców; kontener dostarcza je wszystkim metodom
 * {@code @EventListener} z pasującym typem. Domyślnie synchronicznie, w tym samym wątku.
 * W aplikacji BookingAuditListener używa @TransactionalEventListener(AFTER_COMMIT) - zdarzenie
 * trafia do odbiorcy dopiero po udanym commit.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Publikacja dostarcza zdarzenia wszystkim zarejestrowanym odbiorcom.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Zdarzenia są synchroniczne w tym kontekście. Test nie dowodzi działania TransactionalEventListener.
 */
@DisplayName("Wzorzec Observer przez zdarzenia Springa.")
class ObserverLabTest {

    record SlotCreated(long visitId) {}

    record Publisher(ApplicationEventPublisher events) {
        void createSlot(long id) {
            events.publishEvent(new SlotCreated(id));
        }
    }

    static class AuditObserver {

        final List<Long> seen = new ArrayList<>();

        @EventListener
        void on(SlotCreated event) {
            seen.add(event.visitId());
        }
    }

    static class StatsObserver {

        int count;

        @EventListener
        void on(SlotCreated event) {
            count++;
        }
    }

    /**
     * Publikacja dostarcza zdarzenia wszystkim zarejestrowanym odbiorcom.
     */
    @Test
    @DisplayName("Publikacja dostarcza zdarzenia wszystkim zarejestrowanym odbiorcom")
    void shouldNotifyAllListenersWhenEventIsPublished() {
        new ApplicationContextRunner()
            .withUserConfiguration(Publisher.class, AuditObserver.class, StatsObserver.class)
            .run(context -> {
                context.getBean(Publisher.class).createSlot(7);
                context.getBean(Publisher.class).createSlot(8);

                assertThat(context.getBean(AuditObserver.class).seen).containsExactly(7L, 8L);
                assertThat(context.getBean(StatsObserver.class).count).isEqualTo(2);
            });
    }
}
