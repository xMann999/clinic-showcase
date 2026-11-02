package com.example.clinic.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.dto.BookingSummaryDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.VisitMapper;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.service.BookingConfirmationInbox;
import com.example.clinic.service.VisitRules;
import com.example.clinic.support.FixedClockConfiguration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mapstruct.factory.Mappers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Exercises booking operations with repository doubles, real entities, mapping and fixed-clock rules.
 * Checks observable state, error precedence and event publication without replacing the domain transition.
 */
@ExtendWith(MockitoExtension.class)
class BookingFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private ApplicationEventPublisher events;

    @Mock
    private BookingConfirmationInbox confirmationInbox;

    private BookingFacade facade;
    private User owner;
    private Patient patient;
    private Visit visit;

    @BeforeEach
    void setUp() {
        facade = new BookingFacade(
            userRepository,
            patientRepository,
            visitRepository,
            new VisitRules(FixedClockConfiguration.fixedClock()),
            Mappers.getMapper(VisitMapper.class),
            events,
            FixedClockConfiguration.fixedClock(),
            confirmationInbox
        );
        owner = withId(new User("owner@example.test", "HASH", Role.USER), 1L);
        patient = withId(new Patient("Ewa", "Nowak", "ABC123456", LocalDate.of(1990, 1, 1), null), 10L);
        owner.addPatient(patient);
        Doctor doctor = withId(
            new Doctor("Jan", "Kowalski", "Cardiology", withId(new User("doc@example.test", "HASH", Role.USER), 2L)),
            20L
        );
        visit = withId(new Visit(doctor, LocalDateTime.of(2030, 1, 8, 10, 0), 30), 30L);
    }

    @Test
    void book_validRequest_returnsSummaryWithPatientAndDoctor() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(visitRepository.findById(30L)).thenReturn(Optional.of(visit));

        BookingSummaryDto summary = facade.book(1L, 30L, 10L);

        assertThat(summary.visitId()).isEqualTo(30L);
        assertThat(summary.patient().fullName()).isEqualTo("Ewa Nowak");
        assertThat(summary.doctor().specialization()).isEqualTo("Cardiology");
        assertThat(visit.findPatient()).contains(patient);
        InOrder order = inOrder(userRepository, patientRepository, visitRepository, events);
        order.verify(userRepository).findById(1L);
        order.verify(patientRepository).findById(10L);
        order.verify(visitRepository).findById(30L);
        order.verify(events).publishEvent(any(VisitBookedEvent.class));
    }

    @Test
    void book_patientOfAnotherUser_conflictBeforeTouchingVisit() {
        User stranger = withId(new User("stranger@example.test", "HASH", Role.USER), 99L);
        when(userRepository.findById(99L)).thenReturn(Optional.of(stranger));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> facade.book(99L, 30L, 10L)).isInstanceOfSatisfying(DomainException.class, exception ->
            assertThat(exception.code()).isEqualTo(ErrorCode.OWNERSHIP_MISMATCH)
        );
        verifyNoInteractions(visitRepository, events);
    }

    @Test
    void book_visitAlreadyBooked_stopsAndPublishesNothing() {
        visit.book(patient);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(visitRepository.findById(30L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> facade.book(1L, 30L, 10L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_ALREADY_BOOKED)
            );
        assertThat(visit.findPatient()).contains(patient);
        verifyNoInteractions(events);
    }

    @Test
    void cancel_freeSlot_isNoOp() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(visitRepository.findById(30L)).thenReturn(Optional.of(visit));

        facade.cancel(1L, 30L);

        assertThat(visit.isAvailable()).isTrue();
        verifyNoInteractions(events);
    }

    @Test
    void confirmations_missingUser_failsBeforeReadingHistory() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.getConfirmations(99L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.USER_NOT_FOUND)
            );
        verifyNoInteractions(confirmationInbox, patientRepository, visitRepository, events);
    }

    @Test
    void book_missingUser_failsBeforeLookingUpPatientOrVisit() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.book(99L, 30L, 10L))
            .isInstanceOfSatisfying(DomainException.class, exception -> {
                assertThat(exception.code()).isEqualTo(ErrorCode.USER_NOT_FOUND);
                assertThat(exception.getMessage()).isEqualTo("User with id 99 was not found");
            });
        verifyNoInteractions(patientRepository, visitRepository, events);
    }

    @Test
    void book_missingPatient_failsBeforeLookingUpVisit() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.book(1L, 30L, 10L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.PATIENT_NOT_FOUND)
            );
        verifyNoInteractions(visitRepository, events);
    }

    @Test
    void book_missingVisit_preservesNotFoundCodeAndPublishesNothing() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(visitRepository.findById(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.book(1L, 30L, 10L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_NOT_FOUND)
            );
        verifyNoInteractions(events);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void book_pastVisit_timeFailurePrecedesAvailabilityAndLeavesStateUnchanged(boolean alreadyBooked) {
        Visit past = withId(new Visit(visit.getDoctor(), FixedClockConfiguration.NOW.minusMinutes(15), 30), 30L);
        if (alreadyBooked) {
            past.book(patient);
        }
        var originalPatient = past.findPatient();
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(patientRepository.findById(10L)).thenReturn(Optional.of(patient));
        when(visitRepository.findById(30L)).thenReturn(Optional.of(past));

        assertThatThrownBy(() -> facade.book(1L, 30L, 10L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_IN_PAST)
            );
        assertThat(past.findPatient()).isEqualTo(originalPatient);
        verifyNoInteractions(events);
    }

    @Test
    void get_ownedBooking_usesDetailQueryAndReturnsSummary() {
        visit.book(patient);
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(visitRepository.findWithDoctorAndPatientById(30L)).thenReturn(Optional.of(visit));

        BookingSummaryDto summary = facade.get(1L, 30L);

        assertThat(summary.patient().id()).isEqualTo(10L);
        assertThat(summary.doctor().id()).isEqualTo(20L);
        verifyNoInteractions(patientRepository, events);
    }

    @Test
    void get_missingVisit_preservesVisitNotFoundInsteadOfBookingNotFound() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(owner));
        when(visitRepository.findWithDoctorAndPatientById(30L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> facade.get(1L, 30L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.VISIT_NOT_FOUND)
            );
    }

    @Test
    void cancel_anotherOwnersBooking_rejectsWithoutClearingPatient() {
        visit.book(patient);
        when(userRepository.findById(99L))
            .thenReturn(Optional.of(withId(new User("stranger@example.test", "HASH", Role.USER), 99L)));
        when(visitRepository.findById(30L)).thenReturn(Optional.of(visit));

        assertThatThrownBy(() -> facade.cancel(99L, 30L))
            .isInstanceOfSatisfying(DomainException.class, exception ->
                assertThat(exception.code()).isEqualTo(ErrorCode.OWNERSHIP_MISMATCH)
            );
        assertThat(visit.findPatient()).contains(patient);
        verifyNoInteractions(events);
    }

    // Id nadaje baza; w teście jednostkowym ustawiamy je refleksją, bez publicznego settera.
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
