package com.example.clinic.config;

import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds clinic settings under the {@code clinic} prefix.
 *
 * <p>Boot converts {@code clinic.zone-id} into a {@link ZoneId}; the default is Europe/Warsaw.
 * The record has no mutable components or setters. The configured zone feeds the shared clock.
 *
 * @param zoneId the zone used to interpret local appointment times
 */
@ConfigurationProperties(prefix = "clinic")
public record ClinicProperties(@DefaultValue("Europe/Warsaw") ZoneId zoneId) {}
