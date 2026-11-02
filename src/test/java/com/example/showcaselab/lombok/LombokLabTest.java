package com.example.showcaselab.lombok;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lombok - co generuje procesor adnotacji i gdzie są pułapki.
 * <h2>Cel</h2>
 * Rozpoznać wygenerowany kontrakt konstruktorów i buildera oraz ryzyko metod opartych na mutowalnych relacjach.
 * <h2>Jak działa przykład</h2>
 * Podgląd wygenerowanego kodu: ./mvnw lombok:delombok nie jest skonfigurowany - zamiast tego
 * otwórz target/test-classes/.../LombokLabTest$DefaultedSettings.class w IDE (dekompilacja)
 * albo uruchom: javap -p target/test-classes/com/example/showcaselab/lombok/LombokLabTest$DefaultedSettings.class
 * Mechanizm @Builder.Default: Lombok przenosi inicjalizator pola do statycznej metody
 * $default$active() i dodaje do buildera flagę active$set. build() używa wartości z buildera
 * tylko gdy flaga jest ustawiona; inaczej woła metodę domyślną. Bez @Builder.Default builder
 * nie zna inicjalizatora i wstawia wartość domyślną typu (false, null).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Procesor generuje konstruktory i akcesory.</li>
 * <li>Builder bez Default pomija inicjalizator pola.</li>
 * <li>Builder Default zachowuje wartość do jawnego nadpisania.</li>
 * <li>Każdy builder tworzy niezależną kolekcję.</li>
 * <li>Data z mutowalnym polem narusza wyszukiwanie w HashSet.</li>
 * <li>ToString na cyklicznej relacji przepełnia stos.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Błędne modele pozostają celowe: test sprawdza skutek ich użycia, a nie zaleca tego wzorca.
 */
@DisplayName("Lombok - co generuje procesor adnotacji i gdzie są pułapki.")
class LombokLabTest {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    static class PlainPojo {

        private String name;
        private int age;
    }

    @RequiredArgsConstructor
    @Getter
    static class RequiredOnly {

        private final String required;
        private String optional;
    }

    @Builder
    @Getter
    static class PlainSettings {

        @SuppressWarnings("FieldMayBeFinal")
        private boolean active = true;

        private List<String> tags;
    }

    @Builder
    @Getter
    static class DefaultedSettings {

        @Builder.Default
        private boolean active = true;

        @Builder.Default
        private List<String> tags = new ArrayList<>();
    }

    /**
     * Procesor generuje konstruktory i akcesory.
     * {@code @RequiredArgsConstructor}: tylko pola final (i @NonNull) trafiają do konstruktora.
     */
    @Test
    @DisplayName("Procesor generuje konstruktory i akcesory")
    void shouldGenerateAccessorsWhenAnnotationsAreProcessed() {
        PlainPojo pojo = new PlainPojo("Anna", 30);
        pojo.setAge(31);

        assertThat(pojo.getName()).isEqualTo("Anna");
        assertThat(pojo.getAge()).isEqualTo(31);
        assertThat(new PlainPojo().getName()).isNull();

        assertThat(new RequiredOnly("x").getRequired()).isEqualTo("x");
    }

    /**
     * Builder bez Default pomija inicjalizator pola.
     */
    @Test
    @DisplayName("Builder bez Default pomija inicjalizator pola")
    void shouldIgnoreInitializerWhenBuilderDefaultIsAbsent() {
        assertThat(PlainSettings.builder().build().isActive()).isFalse();
        assertThat(PlainSettings.builder().build().getTags()).isNull();
    }

    /**
     * Builder Default zachowuje wartość do jawnego nadpisania.
     */
    @Test
    @DisplayName("Builder Default zachowuje wartość do jawnego nadpisania")
    void shouldKeepDefaultWhenBuilderValueIsNotProvided() {
        assertThat(DefaultedSettings.builder().build().isActive()).isTrue();
        assertThat(DefaultedSettings.builder().active(false).build().isActive()).isFalse();
    }

    /**
     * Każdy builder tworzy niezależną kolekcję.
     */
    @Test
    @DisplayName("Każdy builder tworzy niezależną kolekcję")
    void shouldCreateIndependentCollectionsWhenBuildingTwoObjects() {
        DefaultedSettings first = DefaultedSettings.builder().build();
        DefaultedSettings second = DefaultedSettings.builder().build();

        first.getTags().add("vip");

        assertThat(second.getTags()).isEmpty();
        assertThat(first.getTags()).isNotSameAs(second.getTags());
    }

    /** Pułapka 1: @Data = equals/hashCode po WSZYSTKICH polach - zmiana pola zmienia hash. */
    @Data
    static class MutableHashPatient {

        private Long id;
        private String lastName;
    }

    /**
     * Data z mutowalnym polem narusza wyszukiwanie w HashSet.
     */
    @Test
    @DisplayName("Data z mutowalnym polem narusza wyszukiwanie w HashSet")
    void shouldLoseSetLookupWhenDataFieldChanges() {
        MutableHashPatient patient = new MutableHashPatient();
        patient.setLastName("Nowak");
        Set<MutableHashPatient> set = new HashSet<>(Set.of(patient));

        patient.setLastName("Kowalska");

        assertThat(set.contains(patient)).isFalse();
        assertThat(set).hasSize(1);
    }

    /** Pułapka 2: @ToString/@EqualsAndHashCode na relacji dwukierunkowej - nieskończona rekurencja. */
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    static class Parent {

        private String name = "p";
        private Child child;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    static class Child {

        private String name = "c";
        private Parent parent;
    }

    /**
     * ToString na cyklicznej relacji przepełnia stos.
     */
    @Test
    @DisplayName("ToString na cyklicznej relacji przepełnia stos")
    void shouldOverflowStackWhenGeneratedMethodsTraverseCycle() {
        Parent parent = new Parent();
        Child child = new Child();
        parent.setChild(child);
        child.setParent(parent);

        assertThatThrownBy(parent::toString).isInstanceOf(StackOverflowError.class);
        assertThatThrownBy(parent::hashCode).isInstanceOf(StackOverflowError.class);
    }
}
