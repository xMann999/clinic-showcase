package com.example.clinic.support;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Stały zegar dla testów: "teraz" = poniedziałek 2030-01-07 08:00 czasu przychodni.
 * @Primary sprawia, że wszędzie, gdzie wstrzykiwany jest Clock, trafia ten bean zamiast
 * systemowego - wynik testów nie zależy od daty ich uruchomienia.
 */
@TestConfiguration(proxyBeanMethods = false)
public class FixedClockConfiguration {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
    public static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 7, 8, 0);
    public static final LocalDateTime TOMORROW_9AM = NOW.plusDays(1).withHour(9);

    public static Clock fixedClock() {
        return Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
    }

    @Bean
    @Primary
    Clock fixedTestClock() {
        return fixedClock();
    }
}
