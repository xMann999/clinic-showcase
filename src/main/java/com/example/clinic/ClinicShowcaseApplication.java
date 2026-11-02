package com.example.clinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the clinic application and defines its scanning boundary.
 *
 * <h2>Responsibility</h2>
 * <p>Bootstraps Spring Boot and discovers clinic components and configuration properties.
 *
 * <h2>How it works</h2>
 * <p>Component scanning begins in {@code com.example.clinic} and includes its subpackages.
 * Spring constructs the discovered beans and supplies their dependencies.
 *
 * <h2>Design choices</h2>
 * <p>Keeping the entry point above clinic packages leaves the separate laboratory packages outside this scan.
 *
 * <h2>Important details</h2>
 * <p>Property records are registered by {@code @ConfigurationPropertiesScan}; they need no component annotation.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ClinicShowcaseApplication {

    /**
     * Starts Spring Boot using the supplied command-line arguments.
     *
     * @param args the startup arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(ClinicShowcaseApplication.class, args);
    }
}
