package com.example.clinic.service;

import com.example.clinic.command.FacilityCommand;
import com.example.clinic.domain.Facility;
import com.example.clinic.dto.FacilityDto;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.FacilityMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages facility data and guards deletion of assigned facilities.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates injected facility and doctor repositories with the facility mapper.
 *
 * <h2>How it works</h2>
 * <p>Creation maps and saves a new entity; updates map into a loaded entity. Deletion checks whether
 * any doctor still references the facility.
 *
 * <h2>Design choices</h2>
 * <p>Relationship changes belong to doctor operations. This service does not cascade deletion to doctors.
 *
 * <h2>Important details</h2>
 * <p>Calls through Spring's transactional proxy start or join a REQUIRED transaction. Calls within
 * this instance do not pass through that proxy. Updates to entities loaded in a write transaction are
 * tracked by the persistence context and synchronized at flush; flush is not commit. Default rollback
 * rules apply to unchecked failures. Read-only operations are hints, not a database permission boundary.
 * The private entity lookup runs inside the public operation's transaction, including subsequent updates
 * and DTO mapping. HTTP validation and sort-property allowlists belong to the controller boundary.
 */
@Service
@RequiredArgsConstructor
public class FacilityService {

    private final FacilityRepository facilityRepository;
    private final DoctorRepository doctorRepository;
    private final FacilityMapper facilityMapper;

    /**
     * Creates and saves a facility without doctor associations.
     *
     * @param command the validated facility data
     * @return the created representation
     */
    @Transactional
    public FacilityDto create(FacilityCommand command) {
        return facilityMapper.toDto(facilityRepository.save(facilityMapper.toEntity(command)));
    }

    /**
     * Loads one facility as an API representation.
     *
     * @param id the facility identifier
     * @return the existing facility representation
     * @throws DomainException if the facility does not exist
     */
    @Transactional(readOnly = true)
    public FacilityDto get(Long id) {
        return facilityMapper.toDto(requireFacility(id));
    }

    /**
     * Lists facilities with the paging and sorting supplied by the caller.
     *
     * @param pageable the requested page with allowed sort properties
     * @return the page content and pagination metadata
     */
    @Transactional(readOnly = true)
    public PageResponse<FacilityDto> list(Pageable pageable) {
        return PageResponse.from(facilityRepository.findAll(pageable).map(facilityMapper::toDto));
    }

    /**
     * Replaces name and address on a managed facility; dirty checking synchronizes the changes.
     *
     * @param id the facility identifier
     * @param command the replacement facility data
     * @return the updated representation
     * @throws DomainException if the facility does not exist
     */
    @Transactional
    public FacilityDto update(Long id, FacilityCommand command) {
        Facility facility = requireFacility(id);
        facilityMapper.updateEntity(command, facility);
        return facilityMapper.toDto(facility);
    }

    /**
     * Deletes an unassigned facility.
     *
     * <p>The reference pre-check supplies a domain error; database constraints still protect concurrent writes.
     *
     * @param id the facility identifier
     * @throws DomainException if the facility is missing or has assigned doctors
     */
    @Transactional
    public void delete(Long id) {
        Facility facility = requireFacility(id);
        if (doctorRepository.existsByFacilitiesId(id)) {
            throw new DomainException(ErrorCode.RESOURCE_IN_USE, "Facility " + id + " has assigned doctors");
        }
        facilityRepository.delete(facility);
    }

    /**
     * Loads a facility entity inside the calling operation's existing transaction.
     *
     * <p>This local lookup does not cross a proxy or open a transaction. The public operation keeps the
     * persistence context open for subsequent mutations and mapping.
     *
     * @param id the facility identifier
     * @return the facility, managed while its persistence context remains open
     * @throws DomainException if the facility does not exist
     */
    private Facility requireFacility(Long id) {
        return facilityRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.FACILITY_NOT_FOUND, "Facility", id));
    }
}
