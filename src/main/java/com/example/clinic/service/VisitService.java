package com.example.clinic.service;

import com.example.clinic.command.VisitCreateCommand;
import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.TimeRange;
import com.example.clinic.domain.Visit;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.VisitDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.error.RequestValidationException;
import com.example.clinic.mapper.VisitMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.repository.VisitSearchCriteria;
import com.example.clinic.repository.VisitSpecifications;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages visit slots and delegates appointment-time rules.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates injected visit and doctor repositories, time rules and DTO mapping for slot creation,
 * reads and deletion. Booking and cancellation belong to {@link com.example.clinic.facade.BookingFacade}.
 *
 * <h2>How it works</h2>
 * <p>Slot creation validates time, locks the doctor, reads that day's slots, checks overlap and saves.
 * Search builds database predicates; deletion rejects booked visits before removing a slot.
 *
 * <h2>Design choices</h2>
 * <p>A doctor lock serializes cooperating slot creators even for an empty schedule. Existing-row booking
 * conflicts use the visit version instead; neither mechanism guarantees all arbitrary SQL writes.
 *
 * <h2>Important details</h2>
 * <p>Calls through Spring's transactional proxy start or join a REQUIRED transaction. Calls within
 * this instance do not pass through that proxy. New slots are explicitly saved and loaded free slots
 * are explicitly deleted; flush synchronizes persistence changes but does not commit the transaction.
 * Default rollback rules apply to unchecked failures. Read-only is a hint, not a database permission boundary.
 * The private entity lookup runs inside the public operation's transaction, including subsequent deletion
 * or DTO mapping. Public operations expose DTOs rather than entities. HTTP validation and sort-property
 * allowlists belong to the controller boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisitService {

    private final VisitRepository visitRepository;
    private final DoctorRepository doctorRepository;
    private final VisitRules visitRules;
    private final VisitMapper visitMapper;

    /**
     * Creates a free slot using the doctor's row as the serialization point.
     *
     * <p>Validates time before acquiring a pessimistic write lock. After the lock is obtained, reads the
     * doctor's slots for that day, rejects overlap and inserts in the same transaction. A newly started
     * transaction requests READ_COMMITTED so a waiting creator can see its predecessor's committed slot.
     * A joined transaction retains its existing isolation. Protection requires every creator to follow
     * this protocol and depends on the database's lock/isolation behavior; direct inserts can bypass it.
     *
     * @param command the validated slot request
     * @return the created free slot
     * @throws DomainException if time is invalid or past, the doctor is missing, or the slot overlaps
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitDto createSlot(VisitCreateCommand command) {
        TimeRange range = visitRules.validateNewSlot(command.dateTime(), command.durationInMinutes());
        Doctor doctor = doctorRepository
            .findByIdForUpdate(command.doctorId())
            .orElseThrow(() -> DomainException.notFound(ErrorCode.DOCTOR_NOT_FOUND, "Doctor", command.doctorId()));
        LocalDateTime dayStart = range.start().toLocalDate().atStartOfDay();
        List<Visit> sameDay = visitRepository.findDoctorVisitsBetween(doctor.getId(), dayStart, dayStart.plusDays(1));
        if (visitRules.collidesWithAny(range, sameDay)) {
            log.info("Slot rejected for doctor {}: overlap at {}", doctor.getId(), range.start());
            throw new DomainException(
                ErrorCode.VISIT_OVERLAP,
                "Doctor " + doctor.getId() + " already has a visit overlapping " + range.start()
            );
        }
        Visit visit = visitRepository.save(new Visit(doctor, range.start(), command.durationInMinutes()));
        log.info("Created visit {} for doctor {}", visit.getId(), doctor.getId());
        return visitMapper.toDto(visit);
    }

    /**
     * Loads one visit as an API representation.
     *
     * @param id the visit identifier
     * @return the existing visit representation
     * @throws DomainException if the visit does not exist
     */
    @Transactional(readOnly = true)
    public VisitDto get(Long id) {
        return visitMapper.toDto(requireVisit(id));
    }

    /**
     * Searches visits using optional database-side predicates.
     *
     * <p>When both bounds are present, {@code from} must be strictly before {@code to}.
     * The interval includes its lower bound and excludes its upper bound.
     *
     * @param criteria the optional filter values
     * @param pageable the requested page and allowed sorting
     * @return matching visit representations with page metadata
     * @throws RequestValidationException if the supplied time bounds are not increasing
     */
    @Transactional(readOnly = true)
    public PageResponse<VisitDto> search(VisitSearchCriteria criteria, Pageable pageable) {
        if (criteria.from() != null && criteria.to() != null && !criteria.from().isBefore(criteria.to())) {
            throw RequestValidationException.single("to", "INVALID_RANGE", "'to' must be after 'from'");
        }
        return PageResponse.from(
            visitRepository.findAll(VisitSpecifications.matching(criteria), pageable).map(visitMapper::toDto)
        );
    }

    /**
     * Deletes a free slot while preserving booked visits.
     *
     * @param id the visit identifier
     * @throws DomainException if the visit is missing or booked
     */
    @Transactional
    public void delete(Long id) {
        Visit visit = requireVisit(id);
        if (!visit.isAvailable()) {
            throw new DomainException(ErrorCode.RESOURCE_IN_USE, "Visit " + id + " is booked and cannot be deleted");
        }
        visitRepository.delete(visit);
    }

    /**
     * Loads a visit entity inside the calling operation's existing transaction.
     *
     * <p>This local lookup does not cross a proxy or open a transaction. The public operation keeps the
     * persistence context open for subsequent deletion or mapping.
     *
     * @param id the visit identifier
     * @return the visit, managed while its persistence context remains open
     * @throws DomainException if the visit does not exist
     */
    private Visit requireVisit(Long id) {
        return visitRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.VISIT_NOT_FOUND, "Visit", id));
    }
}
