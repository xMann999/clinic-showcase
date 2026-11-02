package com.example.showcaselab.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * Trzy sposoby wstrzykiwania zależności.
 * <h2>Cel</h2>
 * Porównać jawność zależności w konstruktorze, setterze i polu, także poza kontenerem.
 * <h2>Jak działa przykład</h2>
 * IoC ("odwrócenie sterowania"): to kontener tworzy obiekty i podaje im zależności;
 * nasza klasa tylko je deklaruje. DI to technika realizacji IoC.
 * W kontenerze wszystkie trzy style działają. Różnica wychodzi POZA kontenerem (np. w teście):
 * - konstruktor: wywołanie wymaga argumentu, pole może być final; odrzucenie null wymaga osobnej kontroli;
 * - setter: obiekt istnieje w stanie niepełnym, dopóki ktoś nie wywoła settera;
 * - pole: w tym przykładzie zależność ustawia Spring - samo "new" pozostawia null.
 * Aplikacja używa konstruktora (Lombok @RequiredArgsConstructor dla pól final).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Kontener uzupełnia konstruktor, setter i pole.</li>
 * <li>Ręczne tworzenie ujawnia brak zależności w polu i setterze.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Małe konteksty nie skanują aplikacji. MiniContainer demonstruje tylko rekurencyjne składanie grafu, bez pełnego cyklu życia Springa.
 */
@DisplayName("Trzy sposoby wstrzykiwania zależności.")
class InjectionStylesLabTest {

    static class GreetingSource {

        String greeting() {
            return "Dzień dobry";
        }
    }

    static class ConstructorInjected {

        private final GreetingSource source;

        ConstructorInjected(GreetingSource source) {
            this.source = source;
        }

        String greet() {
            return source.greeting();
        }
    }

    static class SetterInjected {

        private GreetingSource source;

        @Autowired
        void setSource(GreetingSource source) {
            this.source = source;
        }

        String greet() {
            return source.greeting();
        }
    }

    static class FieldInjected {

        @Autowired
        private GreetingSource source;

        String greet() {
            return source.greeting();
        }
    }

    /**
     * Kontener uzupełnia konstruktor, setter i pole.
     */
    @Test
    @DisplayName("Kontener uzupełnia konstruktor, setter i pole")
    void shouldInjectAllStylesWhenContainerCreatesObjects() {
        try (
            var context = new AnnotationConfigApplicationContext(
                GreetingSource.class,
                ConstructorInjected.class,
                SetterInjected.class,
                FieldInjected.class
            )
        ) {
            assertThat(context.getBean(ConstructorInjected.class).greet()).isEqualTo("Dzień dobry");
            assertThat(context.getBean(SetterInjected.class).greet()).isEqualTo("Dzień dobry");
            assertThat(context.getBean(FieldInjected.class).greet()).isEqualTo("Dzień dobry");
        }
    }

    /**
     * Ręczne tworzenie ujawnia brak zależności w polu i setterze.
     */
    @Test
    @DisplayName("Ręczne tworzenie ujawnia brak zależności w polu i setterze")
    void shouldExposeMissingDependenciesWhenObjectsAreCreatedManually() {
        GreetingSource stub = new GreetingSource() {
            @Override
            String greeting() {
                return "stub";
            }
        };

        assertThat(new ConstructorInjected(stub).greet()).isEqualTo("stub");
        assertThatThrownBy(() -> new SetterInjected().greet()).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FieldInjected().greet()).isInstanceOf(NullPointerException.class);
    }
}
