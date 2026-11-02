package com.example.showcaselab.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Semantyka PATCH - trzy stany pola: nieobecne, jawny null, wartość.
 * <h2>Cel</h2>
 * Odróżnić brak pola od jawnego null i nowej wartości bez utraty informacji podczas odczytu.
 * <h2>Jak działa przykład</h2>
 * Rekord Command z polem String nie odróżni "pola nie wysłano" od "wysłano null" - oba dają null.
 * Optional&lt;String> też nie wystarcza (dwa stany). Drzewo JsonNode rozróżnia wszystkie trzy:
 * has("phoneNumber") == false oznacza brak, isNull() oznacza jawne czyszczenie.
 * Dlatego aplikacja ma PUT (pełna zamiana) i jeden wąski PATCH hasła, a pełny trójstan pokazujemy tu.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Brak pola pozostawia dotychczasową wartość.</li>
 * <li>Jawny null czyści wartość.</li>
 * <li>Wysłana wartość zastępuje dotychczasową.</li>
 * <li>Odczyt i zapis zachowują daty, kwoty i identyfikatory tekstowe.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Używany jest Jackson 3 z pakietów tools.jackson; adnotacje pochodzą z com.fasterxml.jackson.annotation. Konfiguracja lokalnego mappera nie jest konfiguracją Boota.
 */
@DisplayName("Semantyka PATCH - trzy stany pola: nieobecne, jawny null, wartość.")
class PatchSemanticsLabTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    static final class PatientState {

        String phoneNumber = "+48600700800";
    }

    static void applyPhonePatch(PatientState state, JsonNode patch) {
        if (!patch.has("phoneNumber")) {
            return;
        }
        JsonNode value = patch.get("phoneNumber");
        state.phoneNumber = value.isNull() ? null : value.asString();
    }

    /**
     * Brak pola pozostawia dotychczasową wartość.
     */
    @Test
    @DisplayName("Brak pola pozostawia dotychczasową wartość")
    void shouldKeepValueWhenFieldIsMissing() {
        PatientState state = new PatientState();
        applyPhonePatch(state, mapper.readTree("{}"));
        assertThat(state.phoneNumber).isEqualTo("+48600700800");
    }

    /**
     * Jawny null czyści wartość.
     */
    @Test
    @DisplayName("Jawny null czyści wartość")
    void shouldClearValueWhenFieldIsNull() {
        PatientState state = new PatientState();
        applyPhonePatch(state, mapper.readTree("{\"phoneNumber\":null}"));
        assertThat(state.phoneNumber).isNull();
    }

    /**
     * Wysłana wartość zastępuje dotychczasową.
     */
    @Test
    @DisplayName("Wysłana wartość zastępuje dotychczasową")
    void shouldReplaceValueWhenFieldIsPresent() {
        PatientState state = new PatientState();
        applyPhonePatch(state, mapper.readTree("{\"phoneNumber\":\"+48500100200\"}"));
        assertThat(state.phoneNumber).isEqualTo("+48500100200");
    }

    record RoundTrip(LocalDate birthday, LocalDateTime visit, BigDecimal fee, String documentId, Long id) {}

    /**
     * Odczyt i zapis zachowują daty, kwoty i identyfikatory tekstowe.
     * documentId to tekst: "007" jako liczba straciłby wiodące zera.
     * id typu Long powyżej 2^53 nie mieści się dokładnie w number JavaScriptu - klienci JS
     * często przesyłają takie identyfikatory jako tekst.
     */
    @Test
    @DisplayName("Odczyt i zapis zachowują daty, kwoty i identyfikatory tekstowe")
    void shouldPreserveValuesWhenDocumentIsRoundTripped() {
        RoundTrip original = new RoundTrip(
            LocalDate.of(1990, 5, 17),
            LocalDateTime.of(2030, 1, 8, 10, 15),
            new BigDecimal("150.50"),
            "007",
            9_007_199_254_740_993L
        );

        String json = mapper.writeValueAsString(original);

        assertThat(json).contains("\"documentId\":\"007\"", "\"visit\":\"2030-01-08T10:15:00\"", "150.50");
        assertThat(mapper.readValue(json, RoundTrip.class)).isEqualTo(original);
    }
}
