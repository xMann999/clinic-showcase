package com.example.showcaselab.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clinic.validation.QuarterHour;
import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Payload;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.PastOrPresent;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor;

/**
 * Grupy walidacji, walidacja metod (@Validated), @Future/@PastOrPresent, własny walidator @Pesel i zachowanie @QuarterHour dla null.
 * <h2>Cel</h2>
 * Porównać grupy ograniczeń, granice czasu i działanie walidacji wywołań przez proxy.
 * <h2>Jak działa przykład</h2>
 * własny walidator @Pesel i zachowanie @QuarterHour dla null.
 * {@code @Pesel} jest WARIANTEM porównawczym: główne API ma idCardNo (numer dokumentu), który nie jest
 * PESEL-em. Numery w testach są fikcyjne, dobrane tylko pod sumę kontrolną.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Grupy stosują różne reguły do tego samego obiektu.</li>
 * <li>Stały zegar wyznacza granice Future i PastOrPresent.</li>
 * <li>QuarterHour dopuszcza null bez NotNull.</li>
 * <li>Walidator sprawdza długość i sumę kontrolną numeru.</li>
 * <li>Proxy walidacji odrzuca argument przed wywołaniem metody.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * PESEL jest tylko przykładem sumy kontrolnej, nie pełnym sprawdzeniem numeru ani tożsamości osoby.
 */
@DisplayName(
    "Grupy walidacji, walidacja metod (@Validated), @Future/@PastOrPresent, własny walidator @Pesel i zachowanie @QuarterHour dla null."
)
class ValidationLabTest {

    private static final ValidatorFactory FACTORY = Validation.byDefaultProvider()
        .configure()
        .clockProvider(() ->
            java.time.Clock.fixed(java.time.Instant.parse("2030-01-08T10:00:00Z"), java.time.ZoneOffset.UTC)
        )
        .buildValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void close() {
        FACTORY.close();
    }

    /** ---- grupy: te same pola, inne reguły dla tworzenia i aktualizacji ---- */
    interface OnCreate {}

    interface OnUpdate {}

    record PatientForm(
        @Null(groups = OnCreate.class) @NotNull(groups = OnUpdate.class) Long id,
        @NotNull(groups = { OnCreate.class, OnUpdate.class }) String lastName
    ) {}

    /**
     * Grupy stosują różne reguły do tego samego obiektu.
     */
    @Test
    @DisplayName("Grupy stosują różne reguły do tego samego obiektu")
    void shouldApplyDifferentRulesWhenValidationGroupChanges() {
        PatientForm newPatient = new PatientForm(null, "Nowak");

        assertThat(VALIDATOR.validate(newPatient, OnCreate.class)).isEmpty();
        assertThat(VALIDATOR.validate(newPatient, OnUpdate.class))
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("id");
    }

    /** ---- czas: @Future dla terminu, @PastOrPresent dla daty zdarzenia ---- */
    record Appointment(@Future LocalDateTime at, @PastOrPresent LocalDate registeredOn) {}

    /**
     * Stały zegar wyznacza granice Future i PastOrPresent.
     */
    @Test
    @DisplayName("Stały zegar wyznacza granice Future i PastOrPresent")
    void shouldCheckTimeBoundaryWhenClockIsFixed() {
        Appointment valid = new Appointment(LocalDateTime.of(2030, 1, 8, 10, 0).plusDays(1), LocalDate.of(2030, 1, 8));
        Appointment invalid = new Appointment(
            LocalDateTime.of(2030, 1, 8, 10, 0).minusDays(1),
            LocalDate.of(2030, 1, 8).plusDays(1)
        );

        assertThat(VALIDATOR.validate(valid)).isEmpty();
        assertThat(VALIDATOR.validate(invalid))
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactlyInAnyOrder("at", "registeredOn");
        assertThat(VALIDATOR.validate(new Appointment(LocalDateTime.of(2030, 1, 8, 10, 0), LocalDate.of(2030, 1, 8))))
            .extracting(v -> v.getPropertyPath().toString())
            .containsExactly("at");
    }

    /** ---- @QuarterHour z aplikacji: null przepuszcza, obecność wymusza osobne @NotNull ---- */
    record SlotForm(@QuarterHour LocalDateTime start) {}

    /**
     * QuarterHour dopuszcza null bez NotNull.
     */
    @Test
    @DisplayName("QuarterHour dopuszcza null bez NotNull")
    void shouldAcceptNullWhenOnlyQuarterHourIsPresent() {
        assertThat(VALIDATOR.validate(new SlotForm(null))).isEmpty();
        assertThat(VALIDATOR.validate(new SlotForm(LocalDateTime.of(2030, 1, 8, 10, 10)))).hasSize(1);
    }

    /** ---- własne ograniczenie z sumą kontrolną ---- */
    @Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
    @Retention(RetentionPolicy.RUNTIME)
    @Constraint(validatedBy = PeselValidator.class)
    @interface Pesel {
        String message() default "invalid PESEL checksum";

        Class<?>[] groups() default {};

        Class<? extends Payload>[] payload() default {};
    }

    /** Publiczna klasa z publicznym konstruktorem: Hibernate Validator tworzy walidator refleksją. */
    public static class PeselValidator implements ConstraintValidator<Pesel, String> {

        private static final int[] WEIGHTS = { 1, 3, 7, 9, 1, 3, 7, 9, 1, 3 };

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (!value.matches("\\d{11}")) {
                return false;
            }
            int sum = 0;
            for (int index = 0; index < WEIGHTS.length; index++) {
                sum += WEIGHTS[index] * (value.charAt(index) - '0');
            }
            return (10 - (sum % 10)) % 10 == value.charAt(10) - '0';
        }
    }

    record PeselForm(@Pesel String pesel) {}

    /**
     * Walidator sprawdza długość i sumę kontrolną numeru.
     */
    @Test
    @DisplayName("Walidator sprawdza długość i sumę kontrolną numeru")
    void shouldRejectBadChecksumWhenValidatingPesel() {
        assertThat(VALIDATOR.validate(new PeselForm("44051401359"))).isEmpty();
        Set<ConstraintViolation<PeselForm>> wrongChecksum = VALIDATOR.validate(new PeselForm("44051401358"));
        assertThat(wrongChecksum).extracting(ConstraintViolation::getMessage).containsExactly("invalid PESEL checksum");
        assertThat(VALIDATOR.validate(new PeselForm("ABC"))).hasSize(1);
    }

    /** ---- walidacja metod: proxy Springa sprawdza argumenty przed wejściem do metody ---- */
    @Validated
    public static class ReminderScheduler {

        public String schedule(@NotNull @Future LocalDateTime at) {
            return "scheduled " + at;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MethodValidationConfig {

        @Bean
        static MethodValidationPostProcessor methodValidationPostProcessor() {
            MethodValidationPostProcessor processor = new MethodValidationPostProcessor();
            processor.setProxyTargetClass(true);
            processor.setValidator(VALIDATOR);
            return processor;
        }

        @Bean
        ReminderScheduler reminderScheduler() {
            return new ReminderScheduler();
        }
    }

    /**
     * Proxy walidacji odrzuca argument przed wywołaniem metody.
     * Bez proxy (zwykłe new) adnotacje nic nie robią.
     */
    @Test
    @DisplayName("Proxy walidacji odrzuca argument przed wywołaniem metody")
    void shouldRejectArgumentWhenValidationProxyIsUsed() {
        new ApplicationContextRunner()
            .withUserConfiguration(MethodValidationConfig.class)
            .run(context -> {
                ReminderScheduler scheduler = context.getBean(ReminderScheduler.class);

                assertThat(scheduler.schedule(LocalDateTime.of(2030, 1, 8, 10, 0).plusDays(1))).startsWith("scheduled");
                assertThatThrownBy(() -> scheduler.schedule(LocalDateTime.of(2030, 1, 8, 10, 0).minusDays(1))
                ).isInstanceOf(ConstraintViolationException.class);

                assertThat(
                    new ReminderScheduler().schedule(LocalDateTime.of(2030, 1, 8, 10, 0).minusDays(1))
                ).startsWith("scheduled");
            });
    }
}
