package com.example.clinic.facade;

import com.example.clinic.domain.Patient;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.dto.BookingConfirmationDto;
import com.example.clinic.dto.BookingSummaryDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.VisitMapper;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.service.BookingConfirmationInbox;
import com.example.clinic.service.VisitRules;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns booking workflows and keeps their entity access behind a DTO-based application boundary.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates constructor-injected account, patient and visit repositories, appointment-time rules,
 * a visit mapper, the application event publisher, the clock and the local confirmation inbox.
 * Checks account existence and patient ownership; resource CRUD operations remain in their services.
 *
 * <h2>How it works</h2>
 * <p>Proxy-invoked booking and cancellation start or join a REQUIRED write transaction. Repository
 * reads, managed entity changes and DTO mapping stay inside it. Dirty checking synchronizes the
 * patient change and checks the visit version at flush; no additional save is needed on this path.
 * Booking detail uses a read-only transaction and an explicit fetch query. Confirmation history only
 * needs the repository's account-lookup transaction before the in-memory inbox read.
 *
 * <h2>Design choices</h2>
 * <p>Public operations accept identifiers and return DTOs or no result; required entity lookups remain
 * private. Direct repository access keeps the complete booking sequence here without exposing entity
 * loaders on CRUD services. Building summaries before completion permits lazy name access with
 * open-session-in-view disabled. Unchecked failures trigger default transaction rollback.
 *
 * <h2>Important details</h2>
 * <p>Account identifiers check ownership consistency, not authentication. Events and requested logs are
 * produced before commit; {@link com.example.clinic.service.BookingConfirmationListener} prepares a
 * historical receipt only after commit. Version conflicts can still surface at flush/commit and suppress
 * that receipt. The receipt is secondary: its absence or failure does not undo a committed booking.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingFacade {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final VisitRepository visitRepository;

    /** Shared time policy; booking checks the start before asking the entity to enforce availability. */
    private final VisitRules visitRules;
    private final VisitMapper visitMapper;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final BookingConfirmationInbox confirmationInbox;

    /**
     * Books a patient into a free, nonpast visit after checking account ownership.
     *
     * <p>Requires the account and patient, checks ownership, then loads the visit. The time rule runs
     * before the entity checks availability and assigns the patient. Mapping reads names while the
     * surrounding write transaction keeps relationships accessible. Publishes the event and requested
     * log before commit; the listener prepares a local receipt after commit. Unchecked failures roll back
     * the managed change, and optimistic conflicts can also emerge at flush or transaction completion.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     * @param patientId the patient identifier
     * @return the booking summary built before transaction completion
     * @throws DomainException if a resource is missing, ownership differs, or the visit is past or booked
     */
    @Transactional
    public BookingSummaryDto book(Long userId, Long visitId, Long patientId) {
        User user = requireUser(userId);
        Patient patient = requirePatient(patientId);
        if (!patient.belongsTo(user.getId())) {
            throw new DomainException(
                ErrorCode.OWNERSHIP_MISMATCH,
                "Patient " + patientId + " does not belong to user " + userId
            );
        }
        Visit visit = requireVisit(visitId);
        visitRules.ensureNotInPast(visit.getDateTime());
        visit.book(patient);

        BookingSummaryDto summary = visitMapper.toBookingSummary(visit);
        events.publishEvent(
            new VisitBookedEvent(
                UUID.randomUUID(),
                userId,
                visitId,
                summary.dateTime(),
                clock.getZone().getId(),
                summary.durationInMinutes(),
                summary.doctor(),
                clock.instant()
            )
        );

        log.info("Booking requested: visit={}, patient={}", visitId, patientId);
        return summary;
    }

    /**
     * Loads a booking only when its patient belongs to the supplied existing account.
     *
     * <p>Checks the account before fetching the visit with its doctor and optional patient. The read-only
     * transaction covers the query, ownership check and DTO construction. A free slot or another owner's
     * booking produces BOOKING_NOT_FOUND without changing state.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     * @return the booking summary with fetched patient and doctor details
     * @throws DomainException if the account/visit is missing or no booking belongs to this account
     */
    @Transactional(readOnly = true)
    public BookingSummaryDto get(Long userId, Long visitId) {
        requireUser(userId);
        Visit visit = requireVisitWithDetails(visitId);
        return visit
            .findPatient()
            .filter(patient -> patient.belongsTo(userId))
            .map(patient -> visitMapper.toBookingSummary(visit))
            .orElseThrow(() ->
                new DomainException(
                    ErrorCode.BOOKING_NOT_FOUND,
                    "Visit " + visitId + " has no booking for user " + userId
                )
            );
    }

    /**
     * Returns retained booking confirmations after requiring an existing account.
     *
     * <p>The repository's inherited account lookup joins an existing transaction or starts a read-only
     * one; this method adds no surrounding transaction to the in-memory read. Missing accounts fail
     * before the inbox is read, even if stale receipts remain.
     * No entity is accessed after the lookup. Reading does not mark or remove receipts, and cancellation
     * does not rewrite this history.
     *
     * @param userId the recipient account identifier, not an authentication credential
     * @return an immutable snapshot of at most 20 receipts, newest insertion first; empty if none remain
     * @throws DomainException if the account does not exist
     */
    public List<BookingConfirmationDto> getConfirmations(Long userId) {
        requireUser(userId);
        return confirmationInbox.find(userId);
    }

    /**
     * Cancels a booking owned by the supplied account, leaving an already free slot unchanged.
     *
     * <p>Loads the account and visit, rejects another owner's booking, clears the managed patient reference
     * in the same transaction. Historical booking confirmations remain unchanged; they are not current status.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     * @throws DomainException if either resource is missing or the booking belongs to another account
     */
    @Transactional
    public void cancel(Long userId, Long visitId) {
        requireUser(userId);
        Visit visit = requireVisit(visitId);
        var bookedPatient = visit.findPatient();
        if (bookedPatient.isEmpty()) {
            return;
        }
        Patient patient = bookedPatient.get();
        if (!patient.belongsTo(userId)) {
            throw new DomainException(
                ErrorCode.OWNERSHIP_MISMATCH,
                "Visit " + visitId + " is booked by a patient of another user"
            );
        }
        visit.cancelBooking();
    }

    /**
     * Requires an account before accessing its booking or confirmation history.
     *
     * <p>The inherited repository lookup joins a surrounding transaction or opens its own read-only
     * transaction. History reads discard the returned entity; booking operations use it while their
     * transaction remains open. This private helper does not define a separate proxy boundary.
     *
     * @param id the account identifier
     * @return the existing account, managed while its persistence context remains open
     * @throws DomainException if the account does not exist
     */
    private User requireUser(Long id) {
        return userRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User", id));
    }

    /**
     * Loads the patient for an ownership check inside the booking transaction.
     *
     * @param id the patient identifier
     * @return the managed patient; ownership is checked by the calling operation
     * @throws DomainException if the patient does not exist
     */
    private Patient requirePatient(Long id) {
        return patientRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.PATIENT_NOT_FOUND, "Patient", id));
    }

    /**
     * Loads a visit for a state change inside the booking or cancellation transaction.
     *
     * <p>Uses the ordinary identifier lookup without adding a lock or fetch plan. Managed changes are
     * synchronized later, with the existing version column detecting competing updates.
     *
     * @param id the visit identifier
     * @return the managed visit
     * @throws DomainException if the visit does not exist
     */
    private Visit requireVisit(Long id) {
        return visitRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.VISIT_NOT_FOUND, "Visit", id));
    }

    /**
     * Fetches the doctor and optional patient with the visit in the booking-detail transaction.
     *
     * <p>The repository's left patient join retains free slots so the caller can distinguish a missing
     * visit from an existing visit without a booking. This helper does not open a transaction.
     *
     * @param id the visit identifier
     * @return the managed visit with its doctor and optional patient fetched
     * @throws DomainException if the visit does not exist
     */
    private Visit requireVisitWithDetails(Long id) {
        return visitRepository
            .findWithDoctorAndPatientById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.VISIT_NOT_FOUND, "Visit", id));
    }
}
