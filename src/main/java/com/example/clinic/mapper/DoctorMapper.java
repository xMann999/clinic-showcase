package com.example.clinic.mapper;

import com.example.clinic.command.DoctorUpdateCommand;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.dto.DoctorDto;
import com.example.clinic.dto.DoctorListItemDto;
import com.example.clinic.dto.FacilityDto;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;

/**
 * Maps full and lightweight doctor views and descriptive updates.
 *
 * <h2>Responsibility</h2>
 * <p>Maps between persistence/domain objects and command or response boundaries. The generated
 * implementation uses CentralMapperConfig and is supplied as a Spring bean.
 *
 * <h2>How it works</h2>
 * <p>Full detail maps and sorts facilities by identifier; list mapping omits the collection.
 * Updates ignore facilities and use the existing entity.
 *
 * <h2>Design choices</h2>
 * <p>Keeping the list free of collection traversal avoids lazy collection loading per list entry.
 *
 * <h2>Important details</h2>
 * <p>Full mapping needs accessible facilities: fetch them beforehand or keep the persistence context
 * open. Mapping a detail after an update/create can initialize the collection within that transaction.
 * Generated object mappings return null for a null source; generated updates ignore a null command.
 * Handwritten helpers retain their own
 * nonnull preconditions. A mapper neither starts a transaction nor makes an entity managed.
 */
@Mapper(config = CentralMapperConfig.class)
public interface DoctorMapper {
    /**
     * Maps doctor detail including facilities sorted by identifier.
     *
     * <p>Requires accessible lazy relationships; the mapper does not open a persistence context.
     *
     * @param doctor the source or update-target doctor entity
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "doctor", qualifiedByName = "doctorFullName")
    @Mapping(target = "facilities", source = "facilities", qualifiedByName = "sortedFacilities")
    DoctorDto toDto(Doctor doctor);

    /**
     * Maps a lightweight doctor entry without traversing facilities.
     *
     * @param doctor the source or update-target doctor entity
     * @return the mapped representation, or null for a null generated-mapping source
     */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "fullName", source = "doctor", qualifiedByName = "doctorFullName")
    DoctorListItemDto toListItem(Doctor doctor);

    /**
     * Replaces descriptive values on the target doctor while leaving facility associations untouched.
     *
     * @param command the input values for this operation
     * @param doctor the source or update-target doctor entity
     */
    @Mapping(target = "facilities", ignore = true)
    void updateEntity(DoctorUpdateCommand command, @MappingTarget Doctor doctor);

    /**
     * Maps one facility without the inverse doctor collection.
     *
     * @param facility the source, possibly null
     * @return the facility representation, or null
     */
    FacilityDto toFacilityDto(Facility facility);

    /**
     * Derives the full name from a nonnull doctor.
     *
     * @param doctor the source doctor
     * @return the domain display name
     */
    @Named("doctorFullName")
    default String doctorFullName(Doctor doctor) {
        return doctor.fullName();
    }

    /**
     * Maps facilities in ascending nonnull identifier order.
     *
     * <p>The returned stream list is unmodifiable. The source set and its elements must be nonnull,
     * and facility identifiers must be available for comparison.
     *
     * @param facilities the facility representations; the supplied list is retained
     * @return the unmodifiable facility list in identifier order
     */
    @Named("sortedFacilities")
    default List<FacilityDto> sortedFacilities(Set<Facility> facilities) {
        return facilities.stream().sorted(Comparator.comparing(Facility::getId)).map(this::toFacilityDto).toList();
    }
}
