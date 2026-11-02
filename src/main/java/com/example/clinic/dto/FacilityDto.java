package com.example.clinic.dto;

/**
 * Carries a facility's identifier, name and address for API output.
 *
 * <p>Excludes the inverse doctor collection, avoiding recursive relationship serialization.
 * The scalar components are immutable.
 *
 * @param id the resource identifier
 * @param name the facility name
 * @param address the facility address
 */
public record FacilityDto(Long id, String name, String address) {}
