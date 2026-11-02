package com.example.clinic.logging;

import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Logs elapsed service-method time when the demo profile is active.
 *
 * <h2>Responsibility</h2>
 * <p>Observes public methods under the service package through Spring AOP.
 *
 * <h2>How it works</h2>
 * <p>An around advice calls proceed and logs elapsed microseconds in finally, including exceptional
 * completion; the result or failure propagates unchanged.
 *
 * <h2>Design choices</h2>
 * <p>System.nanoTime measures elapsed time. Arguments are omitted because they may contain credentials
 * or personal data.
 *
 * <h2>Important details</h2>
 * <p>Only calls crossing the Spring proxy are intercepted; self-invocation is not. No advice order
 * relative to transactions is declared, so the measured span is not a promised commit duration.
 */
@Slf4j
@Aspect
@Component
@Profile("demo")
public class TimingAspect {

    /**
     * Measures the intercepted call and logs in finally without logging its arguments.
     *
     * @param joinPoint the proxy-intercepted service call
     * @return the underlying result, possibly null
     * @throws Throwable the failure propagated unchanged by the underlying call
     */
    @Around("execution(public * com.example.clinic.service..*(..))")
    public Object measure(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long micros = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - start);
            log.debug("{} took {} us", joinPoint.getSignature().toShortString(), micros);
        }
    }
}
