package com.example.clinic.mapper;

import com.example.clinic.command.PatientCreateCommand;
import com.example.clinic.command.PatientUpdateCommand;
import com.example.clinic.domain.Patient;
import com.example.clinic.dto.PatientDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.ObjectFactory;

/**
 * Maps patient creation, replacement and public response data.
 *
 * <h2>Responsibility</h2>
 * <p>Maps between persistence/domain objects and command or response boundaries. The generated
 * implementation uses CentralMapperConfig and is supplied as a Spring bean.
 *
 * <h2>How it works</h2>
 * <p>Creation uses an object factory; the service assigns ownership. Updates target editable fields.
 * Response mapping derives full name and reads the owner identifier.
 *
 * <h2>Design choices</h2>
 * <p>The public DTO excludes the identity document. Named Java helpers keep derived values checked
 * by the compiler rather than encoded as string expressions.
 *
 * <h2>Important details</h2>
 * <p>Reading an owner identifier need not require the full account state, but loading behavior belongs
 * to the persistence provider. Update mapping can set the optional phone to null.
 * Generated object mappings return null for a null source; generated updates ignore a null command.
 * Handwritten helpers retain their own
 * nonnull preconditions. A mapper neither starts a transaction nor makes an entity managed.
 */
@Mapper(config = CentralMapperConfig.class)
public interface PatientMapper {
    /**
     * Maps public patient fields, derived name and owner identifier without the document number.
     *
     * @param patient the source or update-target patient entity
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "patient", qualifiedByName = "patientFullName")
    PatientDto toDto(Patient patient);

    /**
     * Creates a patient using the object factory; owner assignment and persistence belong to the service.
     *
     * <p>The generated mapper also invokes editable setters after the factory; it does not create a user.
     *
     * @param command the input values for this operation
     * @return the mapped representation, or null for a null generated-mapping source
     */
    Patient toEntity(PatientCreateCommand command);

    /**
     * Constructs a patient from a nonnull command for generated creation mapping.
     *
     * @param command the creation data
     * @return a new patient without an assigned owner or identifier
     */
    @ObjectFactory
    default Patient newPatient(PatientCreateCommand command) {
        return new Patient(
            command.firstName(),
            command.lastName(),
            command.idCardNo(),
            command.birthday(),
            command.phoneNumber()
        );
    }

    /**
     * Replaces editable values on the supplied patient, including clearing a null phone.
     *
     * <p>MappingTarget identifies the object to modify; the service must supply transaction/managed-state
     * preconditions when these changes are intended to be persisted.
     *
     * @param command the input values for this operation
     * @param patient the source or update-target patient entity
     */
    void updateEntity(PatientUpdateCommand command, @MappingTarget Patient patient);

    /**
     * Derives a full name from a nonnull patient using its domain helper.
     *
     * @param patient the source or update-target patient entity
     * @return the derived text
     */
    @Named("patientFullName")
    default String patientFullName(Patient patient) {
        return patient.fullName();
    }
}
