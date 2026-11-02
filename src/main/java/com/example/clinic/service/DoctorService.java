package com.example.clinic.service;

import com.example.clinic.command.DoctorCreateCommand;
import com.example.clinic.command.DoctorUpdateCommand;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.User;
import com.example.clinic.dto.DoctorDto;
import com.example.clinic.dto.DoctorListItemDto;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.DoctorMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.FacilityRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages doctor profiles and facility associations.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates injected repositories and the mapper for doctor operations.
 *
 * <h2>How it works</h2>
 * <p>Loads related accounts and facilities before creation. Relationship helpers update both sides of
 * the association; descriptive updates modify the managed doctor.
 *
 * <h2>Design choices</h2>
 * <p>A lightweight list avoids expanding facilities. Association changes are idempotent; existence and
 * uniqueness pre-checks provide domain errors, while database constraints handle write races.
 *
 * <h2>Important details</h2>
 * <p>Calls through Spring's transactional proxy start or join a REQUIRED transaction. Calls within
 * this instance do not pass through that proxy. Updates to entities loaded in a write transaction are
 * tracked by the persistence context and synchronized at flush; flush is not commit. Default rollback
 * rules apply to unchecked failures. Read-only operations are hints, not a database permission boundary.
 * Private entity lookups run inside the public operation's transaction, including subsequent relationship
 * changes and DTO mapping. HTTP validation and sort-property allowlists belong to the controller boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final FacilityRepository facilityRepository;
    private final VisitRepository visitRepository;
    private final DoctorMapper doctorMapper;

    /**
     * Creates a doctor linked to an existing account and existing facilities.
     *
     * <p>Loads the account, checks account reuse, then resolves all facilities before saving. The database unique
     * constraint remains necessary because concurrent callers can pass the pre-check.
     *
     * @param command the validated doctor data and relationship identifiers
     * @return the created doctor with facilities
     * @throws DomainException if the account or a facility is missing, or the account already has a doctor
     */
    @Transactional
    public DoctorDto create(DoctorCreateCommand command) {
        User user = userRepository
            .findById(command.userId())
            .orElseThrow(() -> DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User", command.userId()));
        if (doctorRepository.existsByUserId(user.getId())) {
            throw new DomainException(ErrorCode.USER_ALREADY_DOCTOR, "User " + user.getId() + " is already a doctor");
        }
        List<Facility> facilities = requireFacilities(command.facilityIds());
        Doctor doctor = new Doctor(command.firstName(), command.lastName(), command.specialization(), user);
        facilities.forEach(doctor::addFacility);
        Doctor saved = doctorRepository.save(doctor);
        log.info("Created doctor {} for user {}", saved.getId(), user.getId());
        return doctorMapper.toDto(saved);
    }

    /**
     * Loads a doctor's detail with the facilities requested by the repository entity graph.
     *
     * @param id the doctor identifier
     * @return the full doctor representation
     * @throws DomainException if the doctor does not exist
     */
    @Transactional(readOnly = true)
    public DoctorDto get(Long id) {
        return doctorRepository
            .findWithFacilitiesById(id)
            .map(doctorMapper::toDto)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.DOCTOR_NOT_FOUND, "Doctor", id));
    }

    /**
     * Lists doctors without expanding their facility collections.
     *
     * @param pageable the paging and allowed sorting selected by the caller
     * @return a page of lightweight doctor representations
     */
    @Transactional(readOnly = true)
    public PageResponse<DoctorListItemDto> list(Pageable pageable) {
        return PageResponse.from(doctorRepository.findAll(pageable).map(doctorMapper::toListItem));
    }

    /**
     * Replaces descriptive fields on the loaded doctor; relationship changes use separate operations.
     *
     * @param id the doctor identifier
     * @param command the replacement descriptive data
     * @return the updated full representation
     * @throws DomainException if the doctor does not exist
     */
    @Transactional
    public DoctorDto update(Long id, DoctorUpdateCommand command) {
        Doctor doctor = requireDoctor(id);
        doctorMapper.updateEntity(command, doctor);
        return doctorMapper.toDto(doctor);
    }

    /**
     * Deletes a doctor only when no visits reference it.
     *
     * <p>Removes join-table associations through the owning collection. Shared facilities and the account
     * remain; the pre-check does not replace foreign-key constraints under concurrency.
     *
     * @param id the doctor identifier
     * @throws DomainException if the doctor is missing or referenced by visits
     */
    @Transactional
    public void delete(Long id) {
        Doctor doctor = requireDoctor(id);
        if (visitRepository.existsByDoctorId(id)) {
            throw new DomainException(ErrorCode.RESOURCE_IN_USE, "Doctor " + id + " has visits");
        }
        doctor.removeAllFacilities();
        doctorRepository.delete(doctor);
    }

    /**
     * Associates an existing facility with an existing doctor, leaving an existing pair unchanged.
     *
     * <p>The domain helper updates owning and inverse collections in the write transaction.
     *
     * @param doctorId the doctor identifier
     * @param facilityId the facility identifier
     * @throws DomainException if either resource does not exist
     */
    @Transactional
    public void assignFacility(Long doctorId, Long facilityId) {
        Doctor doctor = requireDoctor(doctorId);
        Facility facility = requireFacility(facilityId);
        doctor.addFacility(facility);
    }

    /**
     * Removes the association between existing resources; an absent pair is already a successful result.
     *
     * @param doctorId the doctor identifier
     * @param facilityId the facility identifier
     * @throws DomainException if either resource does not exist
     */
    @Transactional
    public void unassignFacility(Long doctorId, Long facilityId) {
        Doctor doctor = requireDoctor(doctorId);
        Facility facility = requireFacility(facilityId);
        doctor.removeFacility(facility);
    }

    /**
     * Loads a doctor entity inside the calling operation's existing transaction.
     *
     * <p>This local lookup does not cross a proxy or open a transaction. The public operation keeps the
     * persistence context open for subsequent mutations and mapping.
     *
     * @param id the doctor identifier
     * @return the doctor, managed while its persistence context remains open
     * @throws DomainException if the doctor does not exist
     */
    private Doctor requireDoctor(Long id) {
        return doctorRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.DOCTOR_NOT_FOUND, "Doctor", id));
    }

    /**
     * Resolves all facilities required for doctor creation in the caller's transaction.
     *
     * <p>Uses one bulk lookup and retains its result order. When any identifier is missing, reports the
     * first missing identifier in the supplied set's iteration order before any doctor is saved.
     * This helper neither opens a transaction nor changes associations.
     *
     * @param facilityIds the validated, non-null set of requested facility identifiers
     * @return the repository result containing every requested facility, possibly empty
     * @throws DomainException if a requested facility does not exist
     */
    private List<Facility> requireFacilities(Set<Long> facilityIds) {
        List<Facility> facilities = facilityRepository.findAllById(facilityIds);
        if (facilities.size() != facilityIds.size()) {
            List<Long> found = facilities.stream().map(Facility::getId).toList();
            Long missing = facilityIds.stream().filter(id -> !found.contains(id)).findFirst().orElseThrow();
            throw DomainException.notFound(ErrorCode.FACILITY_NOT_FOUND, "Facility", missing);
        }
        return facilities;
    }

    /**
     * Loads the facility used by a relationship operation.
     *
     * <p>The calling public operation owns the transaction covering this lookup and association change.
     *
     * @param id the facility identifier
     * @return the existing facility
     * @throws DomainException if the facility does not exist
     */
    private Facility requireFacility(Long id) {
        return facilityRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.FACILITY_NOT_FOUND, "Facility", id));
    }
}
