package com.example.clinic.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

/**
 * Defines shared compile-time mapping policy.
 *
 * <h2>Responsibility</h2>
 * <p>Supplies the Spring component model and unmapped-target error policy for all clinic mappers.
 *
 * <h2>How it works</h2>
 * <p>MapStruct generates Java implementations during compilation; generated mapping uses ordinary
 * method calls, not reflective field copying.
 *
 * <h2>Design choices</h2>
 * <p>A newly unmapped target property fails compilation instead of silently leaving a response incomplete.
 *
 * <h2>Important details</h2>
 * <p>The generated sources can be inspected but are not handwritten application sources. This
 * configuration does not validate input, load relationships or own transactions.
 */
@MapperConfig(componentModel = MappingConstants.ComponentModel.SPRING, unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface CentralMapperConfig {}
