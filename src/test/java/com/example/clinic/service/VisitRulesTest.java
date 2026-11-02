package com.example.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.TimeRange;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.support.FixedClockConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Reguły slotów na stałym zegarze ("teraz" = 2030-01-07 08:00 Europe/Warsaw).
 * Zwykły "new VisitRules(clock)" - reguły nie potrzebują Springa ani bazy.
 */
class VisitRulesTest {

    private final VisitRules rules = new VisitRules(FixedClockConfiguration.fixedClock());
    private static final LocalDateTime TOMORROW_10 = LocalDateTime.of(2030, 1, 8, 10, 0);

    @Test
    void validateNewSlot_validQuarterHourInFuture_returnsComputedRange() {
        TimeRange range = rules.validateNewSlot(TOMORROW_10, 45);

        assertThat(range.end()).isEqualTo(LocalDateTime.of(2030, 1, 8, 10, 45));
    }

    @Test
    void validateNewSlot_minuteNotQuarter_rejected() {
        assertInvalidTime(() -> rules.validateNewSlot(TOMORROW_10.withMinute(10), 30));
    }

    @Test
    void validateNewSlot_secondsOrNanosecondsPresent_rejected() {
        assertInvalidTime(() -> rules.validateNewSlot(TOMORROW_10.withSecond(1), 30));
        assertInvalidTime(() -> rules.validateNewSlot(TOMORROW_10.withNano(1), 30));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -15, 10, 255, 300 })
    void validateNewSlot_invalidDuration_rejected(int minutes) {
        assertInvalidTime(() -> rules.validateNewSlot(TOMORROW_10, minutes));
    }

    @Test
    void validateNewSlot_maximumDuration_accepted() {
        assertThat(rules.validateNewSlot(TOMORROW_10, 240).duration().toMinutes()).isEqualTo(240);
    }

    @Test
    void validateNewSlot_endingExactlyAtMidnight_rejected() {
        // 23:30 + 30 min = 00:00 następnego dnia - poza modelem jednodniowego grafiku.
        assertInvalidTime(() -> rules.validateNewSlot(LocalDateTime.of(2030, 1, 8, 23, 30), 30));
    }

    @Test
    void validateNewSlot_endingAt2345_accepted() {
        assertThat(rules.validateNewSlot(LocalDateTime.of(2030, 1, 8, 23, 30), 15).end()).isEqualTo(
            LocalDateTime.of(2030, 1, 8, 23, 45)
        );
    }

    @Test
    void validateNewSlot_inPast_rejectedWithVisitInPast() {
        assertThatThrownBy(() -> rules.validateNewSlot(LocalDateTime.of(2030, 1, 7, 7, 45), 15)).isInstanceOfSatisfying(
            DomainException.class,
            exception -> assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_IN_PAST)
        );
    }

    @Test
    void validateNewSlot_exactlyNow_accepted() {
        assertThat(rules.validateNewSlot(FixedClockConfiguration.NOW, 15)).isNotNull();
    }

    @Test
    void validateNewSlot_nonexistentLocalTimeOnSpringForward_rejected() {
        // 2030-03-31: w Warszawie zegary przeskakują z 02:00 na 03:00 - 02:15 nie istnieje.
        assertInvalidTime(() -> rules.validateNewSlot(LocalDateTime.of(2030, 3, 31, 2, 15), 15));
    }

    @Test
    void validateNewSlot_ambiguousLocalTimeOnFallBack_rejected() {
        // 2030-10-27: godzina 02:00-03:00 występuje dwa razy - termin byłby niejednoznaczny.
        assertInvalidTime(() -> rules.validateNewSlot(LocalDateTime.of(2030, 10, 27, 2, 15), 15));
    }

    @Test
    void collidesWithAny_overlapOrTouch_detectedCorrectly() {
        Doctor doctor = new Doctor("A", "B", "C", new User("d@example.test", "x", Role.USER));
        List<Visit> existing = List.of(new Visit(doctor, TOMORROW_10, 30));

        assertThat(rules.collidesWithAny(TimeRange.of(TOMORROW_10.plusMinutes(15), 30), existing)).isTrue();
        assertThat(rules.collidesWithAny(TimeRange.of(TOMORROW_10.plusMinutes(30), 30), existing)).isFalse();
        assertThat(rules.collidesWithAny(TimeRange.of(TOMORROW_10, 30), List.of())).isFalse();
    }

    private static void assertInvalidTime(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(DomainException.class, exception ->
            assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_INVALID_TIME)
        );
    }
}
