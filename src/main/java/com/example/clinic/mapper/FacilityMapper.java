package com.example.clinic.mapper;

import com.example.clinic.command.FacilityCommand;
import com.example.clinic.domain.Facility;
import com.example.clinic.dto.FacilityDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ObjectFactory;

/**
 * Maps facility commands and public representations.
 *
 * <h2>Responsibility</h2>
 * <p>Maps between persistence/domain objects and command or response boundaries. The generated
 * implementation uses CentralMapperConfig and is supplied as a Spring bean.
 *
 * <h2>How it works</h2>
 * <p>An object factory invokes the domain constructor for creation. Update mapping changes name
 * and address on the supplied target and explicitly ignores doctors.
 *
 * <h2>Design choices</h2>
 * <p>Ignoring the inverse collection keeps relationship mutations in Doctor's synchronization helpers.
 *
 * <h2>Important details</h2>
 * <p>Mapping into a managed entity can participate in the service's write transaction; MappingTarget
 * alone does not provide that transaction.
 * Generated object mappings return null for a null source; generated updates ignore a null command.
 * Handwritten helpers retain their own
 * nonnull preconditions. A mapper neither starts a transaction nor makes an entity managed.
 */
@Mapper(config = CentralMapperConfig.class)
public interface FacilityMapper {
    /**
     * Maps a facility to its public representation without the doctor collection.
     *
     * @param facility the facility to map or update
     * @return the mapped representation, or null for a null generated-mapping source
     */
    FacilityDto toDto(Facility facility);

    /**
     * Creates a new facility through the object factory without mapping doctor associations.
     *
     * @param command the input values for this operation
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "doctors", ignore = true)
    Facility toEntity(FacilityCommand command);

    /**
     * Constructs a facility from a nonnull command for generated creation mapping.
     *
     * @param command the input values for this operation
     * @return the new facility without persisted identity
     */
    @ObjectFactory
    default Facility newFacility(FacilityCommand command) {
        return new Facility(command.name(), command.address());
    }

    /**
     * Replaces editable facility fields on the supplied target and ignores its doctor collection.
     *
     * @param command the input values for this operation
     * @param facility the facility to map or update
     */
    @Mapping(target = "doctors", ignore = true)
    void updateEntity(FacilityCommand command, @MappingTarget Facility facility);
}
