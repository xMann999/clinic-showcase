package com.example.showcaselab.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonNaming;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.exc.InvalidTypeIdException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson 3 (pakiety tools.jackson.*; adnotacje nadal com.fasterxml.jackson.annotation).
 * <h2>Cel</h2>
 * Powiązać konfigurację mappera i adnotacje modelu ze strukturą JSON oraz rodzajem błędu odczytu.
 * <h2>Jak działa przykład</h2>
 * Zjawisko: zamiana obiektu Javy na tekst JSON i z powrotem, typy błędów, adnotacje.
 * Różnice wobec Jacksona 2 widoczne w tym pliku:
 * - JsonMapper jest niemutowalny; konfiguracja wyłącznie przez builder (brak mapper.configure(...));
 * - java.time jest wbudowane (nie rejestruje się JavaTimeModule), a daty domyślnie są tekstem ISO;
 *   WRITE_DATES_AS_TIMESTAMPS przeniesiono do DateTimeFeature;
 * - JsonParseException zastąpił StreamReadException; wyjątki Jacksona 3 są NIESPRAWDZANE
 *   (JacksonException extends RuntimeException);
 * - JsonSerializer/JsonDeserializer -> ValueSerializer/ValueDeserializer.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Zapis i odczyt JSON zachowują rekord.</li>
 * <li>Format daty zależy od jawnego ustawienia mappera.</li>
 * <li>Różne czasy lokalne mogą wskazywać tę samą chwilę.</li>
 * <li>Składnia, nieznane pole i definicja typu dają różne błędy.</li>
 * <li>Mapper ścisły odrzuca pole ignorowane przez domyślny.</li>
 * <li>Konstruktor JsonCreator tworzy obiekt bez setterów.</li>
 * <li>Adnotacje zmieniają nazwy, widoczność i format pól.</li>
 * <li>Snake case zmienia nazwy pól wskazanego typu.</li>
 * <li>Dyskryminator wybiera tylko zadeklarowane podtypy.</li>
 * <li>Widoki JSON ujawniają różne pola tego samego obiektu.</li>
 * <li>Moduł rejestruje własny zapis i odczyt daty.</li>
 * <li>Typ docelowy i konfiguracja decydują o BigDecimal.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Używany jest Jackson 3 z pakietów tools.jackson; adnotacje pochodzą z com.fasterxml.jackson.annotation. Konfiguracja lokalnego mappera nie jest konfiguracją Boota.
 */
@DisplayName("Jackson 3 (pakiety tools.jackson.*; adnotacje nadal com.fasterxml.jackson.annotation).")
class JacksonBasicsLabTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final JsonMapper strict = JsonMapper.builder()
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build();

    record PatientJson(String firstName, String lastName, LocalDate birthday) {}

    /**
     * Zapis i odczyt JSON zachowują rekord.
     */
    @Test
    @DisplayName("Zapis i odczyt JSON zachowują rekord")
    void shouldPreserveRecordWhenWritingAndReadingJson() {
        PatientJson patient = new PatientJson("Anna", "Nowak", LocalDate.of(1990, 5, 17));

        String json = mapper.writeValueAsString(patient);
        PatientJson back = mapper.readValue(json, PatientJson.class);

        assertThat(mapper.readTree(json)).isEqualTo(
            mapper.readTree("{\"firstName\":\"Anna\",\"lastName\":\"Nowak\",\"birthday\":\"1990-05-17\"}")
        );
        assertThat(back).isEqualTo(patient);
    }

    /**
     * Format daty zależy od jawnego ustawienia mappera.
     */
    @Test
    @DisplayName("Format daty zależy od jawnego ustawienia mappera")
    void shouldChangeDateFormatWhenTimestampsAreEnabled() {
        JsonMapper timestamps = JsonMapper.builder().enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        LocalDate date = LocalDate.of(1990, 5, 17);

        assertThat(mapper.writeValueAsString(date)).isEqualTo("\"1990-05-17\"");
        assertThat(timestamps.writeValueAsString(date)).isEqualTo("[1990,5,17]");
    }

    /**
     * Różne czasy lokalne mogą wskazywać tę samą chwilę.
     * Ćwiczenie z UTC/offsetem: API przychodni używa LocalDateTime bez offsetu (jedna strefa).
     */
    @Test
    @DisplayName("Różne czasy lokalne mogą wskazywać tę samą chwilę")
    void shouldMatchInstantsWhenOffsetsDiffer() {
        OffsetDateTime warsaw = OffsetDateTime.of(LocalDateTime.of(2030, 1, 8, 10, 0), ZoneOffset.ofHours(1));
        OffsetDateTime utc = mapper.readValue("\"2030-01-08T09:00:00Z\"", OffsetDateTime.class);

        assertThat(warsaw.toInstant()).isEqualTo(utc.toInstant());
        assertThat(warsaw.toLocalDateTime()).isNotEqualTo(utc.toLocalDateTime());
    }

    /**
     * Składnia, nieznane pole i definicja typu dają różne błędy.
     * 1. Tekst nie jest poprawnym JSON (składnia) - błąd strumienia.
     * 2. Poprawny JSON, ale pole nieznane typowi - przy ścisłym odczycie.
     * 3. Poprawny JSON, ale Jackson nie wie, JAK utworzyć obiekt (typ abstrakcyjny, brak creatora).
     * Wszystkie są niesprawdzane - kompilator nie wymusza try/catch.
     */
    @Test
    @DisplayName("Składnia, nieznane pole i definicja typu dają różne błędy")
    void shouldDistinguishFailuresWhenInputOrTargetIsInvalid() {
        assertThatThrownBy(() -> mapper.readValue("{\"firstName\":\"Anna\",}", PatientJson.class)).isInstanceOf(
            StreamReadException.class
        );

        assertThatThrownBy(() -> strict.readValue("{\"firstName\":\"Anna\",\"fistName\":\"x\"}", PatientJson.class)
        ).isInstanceOf(UnrecognizedPropertyException.class);

        assertThatThrownBy(() -> mapper.readValue("{\"name\":\"x\"}", AbstractShape.class)).isInstanceOf(
            InvalidDefinitionException.class
        );

        assertThat(RuntimeException.class).isAssignableFrom(JacksonException.class);
    }

    /**
     * Mapper ścisły odrzuca pole ignorowane przez domyślny.
     */
    @Test
    @DisplayName("Mapper ścisły odrzuca pole ignorowane przez domyślny")
    void shouldRejectUnknownFieldWhenStrictModeIsEnabled() {
        PatientJson lenient = mapper.readValue("{\"firstName\":\"Anna\",\"extra\":1}", PatientJson.class);

        assertThat(lenient.firstName()).isEqualTo("Anna");
        assertThatThrownBy(() -> strict.readValue("{\"firstName\":\"Anna\",\"extra\":1}", PatientJson.class)
        ).isInstanceOf(UnrecognizedPropertyException.class);
    }

    abstract static class AbstractShape {

        public String name;
    }

    static class PojoWithCreator {

        private final String code;

        @JsonCreator
        PojoWithCreator(@JsonProperty("code") String code) {
            this.code = code.toUpperCase(java.util.Locale.ROOT);
        }

        public String getCode() {
            return code;
        }
    }

    /**
     * Konstruktor JsonCreator tworzy obiekt bez setterów.
     */
    @Test
    @DisplayName("Konstruktor JsonCreator tworzy obiekt bez setterów")
    void shouldUseConstructorWhenJsonCreatorIsPresent() {
        assertThat(mapper.readValue("{\"code\":\"abc\"}", PojoWithCreator.class).getCode()).isEqualTo("ABC");
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    static class AnnotatedDto {

        @JsonProperty("patient_name")
        public String name = "Anna";

        @JsonIgnore
        public String idCardNo = "ABC123456";

        public String phone = null;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy")
        public LocalDate birthday = LocalDate.of(1990, 5, 17);
    }

    /**
     * Adnotacje zmieniają nazwy, widoczność i format pól.
     */
    @Test
    @DisplayName("Adnotacje zmieniają nazwy, widoczność i format pól")
    void shouldApplyFieldRulesWhenAnnotationsArePresent() {
        String json = mapper.writeValueAsString(new AnnotatedDto());

        assertThat(json)
            .contains("\"patient_name\":\"Anna\"", "\"birthday\":\"17.05.1990\"")
            .doesNotContain("idCardNo", "ABC123456", "phone");
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record SnakeCase(String firstName, Long userId) {}

    /**
     * Snake case zmienia nazwy pól wskazanego typu.
     */
    @Test
    @DisplayName("Snake case zmienia nazwy pól wskazanego typu")
    void shouldUseSnakeCaseWhenTypeDeclaresNamingStrategy() {
        assertThat(mapper.readTree(mapper.writeValueAsString(new SnakeCase("Anna", 7L)))).isEqualTo(
            mapper.readTree("{\"first_name\":\"Anna\",\"user_id\":7}")
        );
    }

    /** Pole {@code type} wybiera jeden z jawnie zadeklarowanych podtypów. */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes(
        {
            @JsonSubTypes.Type(value = SmsReminder.class, name = "sms"),
            @JsonSubTypes.Type(value = EmailReminder.class, name = "email"),
        }
    )
    sealed interface Reminder permits SmsReminder, EmailReminder {}

    record SmsReminder(String phone) implements Reminder {}

    record EmailReminder(String email) implements Reminder {}

    /**
     * Dyskryminator wybiera tylko zadeklarowane podtypy.
     */
    @Test
    @DisplayName("Dyskryminator wybiera tylko zadeklarowane podtypy")
    void shouldRejectUnknownSubtypeWhenDiscriminatorIsClosed() {
        Reminder reminder = mapper.readValue("{\"type\":\"sms\",\"phone\":\"+48600\"}", Reminder.class);

        assertThat(reminder).isEqualTo(new SmsReminder("+48600"));
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"java.lang.Runtime\"}", Reminder.class)).isInstanceOf(
            InvalidTypeIdException.class
        );
    }

    static class Views {

        interface Public {}

        interface Internal extends Public {}
    }

    static class ViewedPatient {

        @JsonView(Views.Public.class)
        public String name = "Anna";

        @JsonView(Views.Internal.class)
        public String idCardNo = "ABC123456";
    }

    /**
     * Widoki JSON ujawniają różne pola tego samego obiektu.
     */
    @Test
    @DisplayName("Widoki JSON ujawniają różne pola tego samego obiektu")
    void shouldExposeDifferentFieldsWhenViewChanges() {
        String publicJson = mapper.writerWithView(Views.Public.class).writeValueAsString(new ViewedPatient());
        String internalJson = mapper.writerWithView(Views.Internal.class).writeValueAsString(new ViewedPatient());

        assertThat(publicJson).contains("Anna").doesNotContain("ABC123456");
        assertThat(internalJson).contains("Anna", "ABC123456");
    }

    /** Starszy system zapisuje datę jako "17/05/1990" - własna para serializer/deserializer. */
    static class LegacyDateSerializer extends ValueSerializer<LocalDate> {

        @Override
        public void serialize(LocalDate value, JsonGenerator gen, SerializationContext context) {
            gen.writeString("%02d/%02d/%d".formatted(value.getDayOfMonth(), value.getMonthValue(), value.getYear()));
        }
    }

    static class LegacyDateDeserializer extends ValueDeserializer<LocalDate> {

        @Override
        public LocalDate deserialize(JsonParser parser, DeserializationContext context) {
            String[] parts = parser.getString().split("/");
            return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
        }
    }

    /**
     * Moduł rejestruje własny zapis i odczyt daty.
     */
    @Test
    @DisplayName("Moduł rejestruje własny zapis i odczyt daty")
    void shouldUseCustomDateFormatWhenModuleIsRegistered() {
        SimpleModule legacy = new SimpleModule("legacy-dates")
            .addSerializer(LocalDate.class, new LegacyDateSerializer())
            .addDeserializer(LocalDate.class, new LegacyDateDeserializer());
        JsonMapper legacyMapper = JsonMapper.builder().addModule(legacy).build();

        assertThat(legacyMapper.writeValueAsString(LocalDate.of(1990, 5, 17))).isEqualTo("\"17/05/1990\"");
        assertThat(legacyMapper.readValue("\"17/05/1990\"", LocalDate.class)).isEqualTo(LocalDate.of(1990, 5, 17));
    }

    record Price(BigDecimal amount) {}

    /**
     * Typ docelowy i konfiguracja decydują o BigDecimal.
     */
    @Test
    @DisplayName("Typ docelowy i konfiguracja decydują o BigDecimal")
    @SuppressWarnings("unchecked")
    void shouldPreserveDecimalScaleWhenTargetTypeIsBigDecimal() {
        String json = "{\"amount\":0.10}";

        assertThat(mapper.readValue(json, Price.class).amount()).isEqualByComparingTo("0.10").hasScaleOf(2);
        Map<String, Object> asDouble = mapper.readValue(json, Map.class);
        Map<String, Object> asBigDecimal = JsonMapper.builder()
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build()
            .readValue(json, Map.class);

        assertThat(asDouble.get("amount")).isInstanceOf(Double.class);
        assertThat(asBigDecimal.get("amount")).isEqualTo(new BigDecimal("0.10"));
    }
}
