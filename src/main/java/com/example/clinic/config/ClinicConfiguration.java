package com.example.clinic.config;

import java.time.Clock;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.DeserializationFeature;

/**
 * Supplies shared time, password encoding and JSON customization.
 *
 * <h2>Responsibility</h2>
 * <p>Provides library objects consumed through dependency injection.
 *
 * <h2>How it works</h2>
 * <p>Spring registers the {@code @Bean} results with singleton scope. The Jackson customizer
 * modifies Boot's builder rather than supplying a replacement mapper.
 *
 * <h2>Design choices</h2>
 * <p>Central definitions let services accept a clock or encoder without constructing them internally.
 *
 * <h2>Important details</h2>
 * <p>{@code proxyBeanMethods = false} means direct calls between bean methods are ordinary Java calls.
 * These methods receive dependencies as parameters instead.
 */
@Configuration(proxyBeanMethods = false)
public class ClinicConfiguration {

    /**
     * Provides the clock used by visit rules and error timestamps.
     *
     * <p>The configured zone controls local appointment calculations. Replacing this bean with a fixed
     * clock permits deterministic time-dependent checks; direct system-clock calls would bypass it.
     *
     * @param properties the bound clinic settings
     * @return a system clock in the configured zone
     */
    @Bean
    Clock clinicClock(ClinicProperties properties) {
        return Clock.system(properties.zoneId());
    }

    /**
     * Provides BCrypt password hashing for account creation and password changes.
     *
     * <p>BCrypt uses a random salt; verify a candidate with {@code matches}, rather than comparing hash strings.
     * This encoder supplies hashing only; it does not install authentication or authorization.
     *
     * @return the shared password encoder
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Rejects unknown JSON properties through Boot's Jackson 3 builder.
     *
     * <p>This customization retains Boot's mapper construction and turns misspelled request fields into
     * binding failures handled by the web advice. It does not replace Bean Validation.
     *
     * @return the builder customization
     */
    @Bean
    JsonMapperBuilderCustomizer strictJsonReading() {
        return builder -> builder.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }
}
