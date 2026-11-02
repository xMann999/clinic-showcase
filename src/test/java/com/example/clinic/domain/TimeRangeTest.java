package com.example.clinic.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Value Object TimeRange: równość po wartościach i reguła półotwartych przedziałów.
 */
class TimeRangeTest {

    private static final LocalDateTime T10 = LocalDateTime.of(2030, 1, 8, 10, 0);

    private static TimeRange range(int startMinuteOffset, int minutes) {
        return TimeRange.of(T10.plusMinutes(startMinuteOffset), minutes);
    }

    @Test
    void equals_sameBoundaries_equalAsValues() {
        assertThat(range(0, 30)).isEqualTo(range(0, 30)).hasSameHashCodeAs(range(0, 30));
        assertThat(range(0, 30)).isNotEqualTo(range(0, 45));
    }

    @Test
    void constructor_endNotAfterStart_rejected() {
        assertThatThrownBy(() -> new TimeRange(T10, T10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TimeRange(T10, T10.minusMinutes(15))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void overlaps_touchingRanges_noCollision() {
        // 10:00-10:30 i 10:30-11:00 stykają się końcami - to dozwolone.
        assertThat(range(0, 30).overlaps(range(30, 30))).isFalse();
        assertThat(range(30, 30).overlaps(range(0, 30))).isFalse();
    }

    @Test
    void overlaps_partialFromBothSides_collision() {
        assertThat(range(0, 30).overlaps(range(15, 30))).isTrue();
        assertThat(range(15, 30).overlaps(range(0, 30))).isTrue();
    }

    @Test
    void overlaps_containmentAndIdentity_collision() {
        assertThat(range(0, 60).overlaps(range(15, 15))).isTrue();
        assertThat(range(15, 15).overlaps(range(0, 60))).isTrue();
        assertThat(range(0, 30).overlaps(range(0, 30))).isTrue();
    }

    @Test
    void overlaps_disjoint_noCollision() {
        assertThat(range(0, 30).overlaps(range(60, 30))).isFalse();
    }
}
