package com.example.clinic.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.example.clinic.domain.Visit;
import com.example.clinic.mapper.VisitMapper;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Atomowość rezerwacji: awaria PO zmianie wizyty, ale PRZED końcem metody fasady,
 * cofa całą transakcję. Mapper (prawdziwy bean, owinięty spy) rzuca wyjątek przy budowaniu
 * podsumowania - czyli w środku operacji, po visit.book(...).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class BookingRollbackTest {

    @Autowired
    private BookingFacade bookingFacade;

    @Autowired
    private VisitRepository visitRepository;

    @Autowired
    private TestFixtures fixtures;

    @MockitoSpyBean
    private VisitMapper visitMapper;

    @Test
    void failureAfterBookingInsideFacade_rollsBackWholeOperation() {
        Long doctorId = fixtures.doctor();
        Long visitId = fixtures.visit(doctorId, FixedClockConfiguration.TOMORROW_9AM, 30);
        Long userId = fixtures.user();
        Long patientId = fixtures.patient(userId);
        doThrow(new IllegalStateException("simulated failure while building the response"))
            .when(visitMapper)
            .toBookingSummary(any(Visit.class));

        assertThatThrownBy(() -> bookingFacade.book(userId, visitId, patientId)).isInstanceOf(
            IllegalStateException.class
        );

        Visit reloaded = fixtures
            .transactionTemplate()
            .execute(status -> visitRepository.findById(visitId).orElseThrow());
        assertThat(reloaded.isAvailable()).isTrue();
        assertThat(reloaded.getVersion()).isZero();
    }
}
