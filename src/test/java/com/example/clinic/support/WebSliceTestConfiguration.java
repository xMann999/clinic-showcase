package com.example.clinic.support;

import com.example.clinic.config.ClinicConfiguration;
import com.example.clinic.config.ClinicProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Dodatki dla wycinka @WebMvcTest. Slice skanuje tylko kontrolery, advice, filtry i
 * WebMvcConfigurer - zwykłe klasy @Configuration (Clock, customizer JSON) trzeba dołączyć
 * jawnie, aby advice miał zegar, a JSON był czytany tak samo ściśle jak w aplikacji.
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(ClinicProperties.class)
@Import({ ClinicConfiguration.class, FixedClockConfiguration.class })
public class WebSliceTestConfiguration {}
