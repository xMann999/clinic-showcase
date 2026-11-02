package com.example.showcaselab.tx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Kolejność advice @Around/@Before/@After w jednym aspekcie.
 * <h2>Cel</h2>
 * Porównać kolejność advice przy sukcesie i błędzie bez przenoszenia logiki biznesowej do aspektu.
 * <h2>Jak działa przykład</h2>
 * Spring AOP (proxy w runtime, tylko wywołania metod beanów) używa adnotacji AspectJ, ale nie
 * pełnego AspectJ (tkanie bajtkodu przy kompilacji/ładowaniu, także pola i konstruktory).
 * W obrębie jednego aspektu: @Around otacza wszystko, @Before przed metodą, @After zawsze po niej
 * (także po wyjątku - jak finally). Logika biznesowa zostaje w serwisie, aspekt tylko obserwuje.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Advice otaczają poprawne wywołanie w ustalonej kolejności.</li>
 * <li>After wykonuje się również po błędzie metody.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test nie jest otoczony transakcją testową. Wyniki izolacji dotyczą H2; NESTED wymaga obsługi savepointów JDBC.
 */
@SpringJUnitConfig(AdviceOrderLabTest.Config.class)
@DisplayName("Kolejność advice @Around/@Before/@After w jednym aspekcie.")
class AdviceOrderLabTest {

    @lombok.RequiredArgsConstructor
    static class SlotCalculator {

        final List<String> events;

        public int slotsInHour(int minutes) {
            events.add("target");
            if (minutes <= 0) {
                throw new IllegalArgumentException("minutes");
            }
            return 60 / minutes;
        }
    }

    @Aspect
    @lombok.RequiredArgsConstructor
    static class RecordingAspect {

        private final List<String> events;

        @Around("execution(* com.example.showcaselab.tx.AdviceOrderLabTest.SlotCalculator.*(..))")
        public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
            events.add("around:before");
            try {
                return joinPoint.proceed();
            } finally {
                events.add("around:after");
            }
        }

        @Before("execution(* com.example.showcaselab.tx.AdviceOrderLabTest.SlotCalculator.*(..))")
        public void before() {
            events.add("before");
        }

        @After("execution(* com.example.showcaselab.tx.AdviceOrderLabTest.SlotCalculator.*(..))")
        public void after() {
            events.add("after");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAspectJAutoProxy
    static class Config {

        @Bean
        List<String> events() {
            return new ArrayList<>();
        }

        @Bean
        SlotCalculator slotCalculator(List<String> events) {
            return new SlotCalculator(events);
        }

        @Bean
        RecordingAspect recordingAspect(List<String> events) {
            return new RecordingAspect(events);
        }
    }

    @Autowired
    private SlotCalculator calculator;

    @Autowired
    private List<String> events;

    /**
     * Advice otaczają poprawne wywołanie w ustalonej kolejności.
     */
    @Test
    @DisplayName("Advice otaczają poprawne wywołanie w ustalonej kolejności")
    void shouldRecordAdviceOrderWhenTargetSucceeds() {
        events.clear();
        assertThat(calculator.slotsInHour(15)).isEqualTo(4);
        assertThat(events).containsExactly("around:before", "before", "target", "after", "around:after");
    }

    /**
     * After wykonuje się również po błędzie metody.
     */
    @Test
    @DisplayName("After wykonuje się również po błędzie metody")
    void shouldRunAfterAdviceWhenTargetFails() {
        events.clear();
        assertThatThrownBy(() -> calculator.slotsInHour(0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(events).containsExactly("around:before", "before", "target", "after", "around:after");
    }
}
