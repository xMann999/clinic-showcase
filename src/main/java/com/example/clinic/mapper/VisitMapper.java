package com.example.clinic.mapper;

import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Visit;
import com.example.clinic.dto.BookingSummaryDto;
import com.example.clinic.dto.DoctorSummaryDto;
import com.example.clinic.dto.PatientSummaryDto;
import com.example.clinic.dto.VisitDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Maps visit views and nested booking summaries.
 *
 * <h2>Responsibility</h2>
 * <p>Maps between persistence/domain objects and command or response boundaries. The generated
 * implementation uses CentralMapperConfig and is supplied as a Spring bean.
 *
 * <h2>How it works</h2>
 * <p>Free visits map to a null patient identifier/summary. Booking detail reads patient and doctor names.
 *
 * <h2>Design choices</h2>
 * <p>DTOs represent absence without dummy patient objects and omit full persistence relationships.
 *
 * <h2>Important details</h2>
 * <p>Booking summaries traverse lazy name fields and require fetched relationships or an open context.
 * Simple slot mapping reads related identifiers rather than full personal profiles.
 * Generated object mappings return null for a null source; generated updates ignore a null command.
 * Handwritten helpers retain their own
 * nonnull preconditions. A mapper neither starts a transaction nor makes an entity managed.
 */
@Mapper(config = CentralMapperConfig.class)
public interface VisitMapper {
    /**
     * Maps slot data and related identifiers; free slots have a null patient identifier.
     *
     * @param visit the source visit with relationships accessible as required above
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "patientId", source = "visit", qualifiedByName = "patientId")
    VisitDto toDto(Visit visit);

    /**
     * Maps booking detail, reading doctor and optional patient names.
     *
     * <p>Relations must be fetched or accessible in an open persistence context; a free slot produces
     * a null patient summary rather than raising a booking-not-found error.
     *
     * @param visit the source visit with relationships accessible as required above
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "visitId", source = "id")
    @Mapping(target = "patient", source = "visit", qualifiedByName = "patientSummary")
    @Mapping(target = "doctor", source = "visit", qualifiedByName = "doctorSummary")
    BookingSummaryDto toBookingSummary(Visit visit);

    /**
     * Returns the booked patient's identifier or null for a free nonnull visit.
     *
     * @param visit the source visit with relationships accessible as required above
     * @return the booked patient identifier, or null for a free slot
     */
    @Named("patientId")
    default Long patientId(Visit visit) {
        return visit.findPatient().map(Patient::getId).orElse(null);
    }

    /**
     * Returns a name-only patient summary or null for a free nonnull visit.
     *
     * @param visit the source visit with relationships accessible as required above
     * @return the patient summary, or null for a free slot
     */
    @Named("patientSummary")
    default PatientSummaryDto patientSummary(Visit visit) {
        return visit
            .findPatient()
            .map(patient -> new PatientSummaryDto(patient.getId(), patient.fullName()))
            .orElse(null);
    }

    /**
     * Builds the required doctor's summary from a nonnull visit with accessible doctor state.
     *
     * @param visit the source visit with relationships accessible as required above
     * @return the required doctor's summary
     */
    @Named("doctorSummary")
    default DoctorSummaryDto doctorSummary(Visit visit) {
        return new DoctorSummaryDto(
            visit.getDoctor().getId(),
            visit.getDoctor().fullName(),
            visit.getDoctor().getSpecialization()
        );
    }
}
