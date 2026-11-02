package com.example.clinic.service;

import com.example.clinic.command.PatientCreateCommand;
import com.example.clinic.command.PatientUpdateCommand;
import com.example.clinic.domain.EmailAddress;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.PatientDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.PatientMapper;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages patients, ownership and patient searches.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates injected repositories and the patient mapper; account creation is outside this service.
 *
 * <h2>How it works</h2>
 * <p>Loads the owner, checks the document number, synchronizes ownership and saves a new patient.
 * Updates modify a loaded entity; deletion first checks visit references.
 *
 * <h2>Design choices</h2>
 * <p>API mapping excludes the document number. Identifier-only creation logs avoid patient names and
 * documents. Document uniqueness pre-checks are not atomic; the database constraint is still required.
 *
 * <h2>Important details</h2>
 * <p>Calls through Spring's transactional proxy start or join a REQUIRED transaction. Calls within
 * this instance do not pass through that proxy. Updates to entities loaded in a write transaction are
 * tracked by the persistence context and synchronized at flush; flush is not commit. Default rollback
 * rules apply to unchecked failures. Read-only operations are hints, not a database permission boundary.
 * Private entity lookups run inside the public operation's transaction, including subsequent updates
 * and DTO mapping. Public operations expose DTOs rather than entities. HTTP validation and sort-property
 * allowlists belong to the controller boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PatientService {

    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final VisitRepository visitRepository;
    private final PatientMapper patientMapper;

    /**
     * Creates a patient for an existing owner in one write transaction.
     *
     * <p>Synchronizes both relationship sides and explicitly saves the patient because ownership has no
     * persist cascade. Logs identifiers rather than personal details.
     *
     * @param command the validated patient data and owner identifier
     * @return the created representation without the document number
     * @throws DomainException if the owner is missing or the document number is already used
     */
    @Transactional
    public PatientDto create(PatientCreateCommand command) {
        User owner = userRepository
            .findById(command.userId())
            .orElseThrow(() -> DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User", command.userId()));
        if (patientRepository.existsByIdCardNo(command.idCardNo())) {
            throw new DomainException(
                ErrorCode.ID_CARD_ALREADY_EXISTS,
                "A patient with this identity document already exists"
            );
        }
        Patient patient = patientMapper.toEntity(command);
        owner.addPatient(patient);
        Patient saved = patientRepository.save(patient);

        log.info("Created patient {} for user {}", saved.getId(), owner.getId());
        return patientMapper.toDto(saved);
    }

    /**
     * Loads a patient and maps only the fields exposed by the API.
     *
     * @param id the patient identifier
     * @return the public patient representation
     * @throws DomainException if the patient does not exist
     */
    @Transactional(readOnly = true)
    public PatientDto get(Long id) {
        return patientMapper.toDto(requirePatient(id));
    }

    /**
     * Searches patients using normalized email in preference to a last-name fragment.
     *
     * <p>Null or blank filters are absent. Without email, a nonblank surname is stripped and matched
     * case-insensitively; without either filter the repository lists all patients.
     *
     * @param email the optional exact owning-account email
     * @param lastName the optional last-name fragment
     * @param pageable the requested page and allowed sorting
     * @return the matching patient representations and page metadata
     */
    @Transactional(readOnly = true)
    public PageResponse<PatientDto> search(String email, String lastName, Pageable pageable) {
        return PageResponse.from(findPatients(email, lastName, pageable).map(patientMapper::toDto));
    }

    /**
     * Selects the patient query, giving a nonblank email precedence over a surname fragment.
     *
     * <p>Email is normalized; surname input is stripped only when no email filter applies.
     * Null and blank filters are absent. The caller's read transaction covers this lookup and subsequent
     * DTO mapping; this helper neither opens a transaction nor materializes an unpaged result.
     *
     * @param email the optional exact owning-account email
     * @param lastName the optional case-insensitive surname fragment
     * @param pageable the paging and sorting passed unchanged to the repository
     * @return the repository page, including its original totals and possibly empty content
     */
    private Page<Patient> findPatients(String email, String lastName, Pageable pageable) {
        if (email != null && !email.isBlank()) {
            return patientRepository.findAllByUserEmail(EmailAddress.normalize(email), pageable);
        }
        if (lastName != null && !lastName.isBlank()) {
            return patientRepository.findAllByLastNameContainingIgnoreCase(lastName.strip(), pageable);
        }
        return patientRepository.findAll(pageable);
    }

    /**
     * Replaces editable patient fields while retaining ownership.
     *
     * <p>A changed document number is checked for reuse before mapping. Null phone clears the phone.
     * The managed entity is synchronized by dirty checking in this write transaction.
     *
     * @param id the patient identifier
     * @param command the replacement editable values
     * @return the updated public representation
     * @throws DomainException if the patient is missing or the document number is already used
     */
    @Transactional
    public PatientDto update(Long id, PatientUpdateCommand command) {
        Patient patient = requirePatient(id);
        if (
            !command.idCardNo().equals(patient.getIdCardNo()) && patientRepository.existsByIdCardNo(command.idCardNo())
        ) {
            throw new DomainException(
                ErrorCode.ID_CARD_ALREADY_EXISTS,
                "A patient with this identity document already exists"
            );
        }
        patientMapper.updateEntity(command, patient);
        return patientMapper.toDto(patient);
    }

    /**
     * Deletes a patient only when no visits reference it.
     *
     * @param id the patient identifier
     * @throws DomainException if the patient is missing or referenced by a visit
     */
    @Transactional
    public void delete(Long id) {
        Patient patient = requirePatient(id);
        if (visitRepository.existsByPatientId(id)) {
            throw new DomainException(ErrorCode.RESOURCE_IN_USE, "Patient " + id + " has visits");
        }
        patientRepository.delete(patient);
    }

    /**
     * Loads a patient entity inside the calling operation's existing transaction.
     *
     * <p>This local lookup does not cross a proxy or open a transaction. The public operation keeps the
     * persistence context open for subsequent mutations and mapping.
     *
     * @param id the patient identifier
     * @return the patient, managed while its persistence context remains open
     * @throws DomainException if the patient does not exist
     */
    private Patient requirePatient(Long id) {
        return patientRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.PATIENT_NOT_FOUND, "Patient", id));
    }
}
