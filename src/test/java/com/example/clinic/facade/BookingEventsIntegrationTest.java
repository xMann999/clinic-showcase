package com.example.clinic.facade;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.clinic.dto.DoctorSummaryDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.service.BookingConfirmationInbox;
import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class BookingEventsIntegrationTest {

    @Autowired
    BookingFacade facade;

    @Autowired
    TestFixtures fixtures;

    @Autowired
    VisitRepository visits;

    @Autowired
    ApplicationEventPublisher publisher;

    @Autowired
    MockMvc mvc;

    @MockitoSpyBean
    BookingConfirmationInbox inbox;

    Long userId;
    Long patientId;
    Long visitId;

    @BeforeEach
    void setUp() {
        userId = fixtures.user();
        patientId = fixtures.patient(userId);
        visitId = fixtures.visit(fixtures.doctor(), FixedClockConfiguration.TOMORROW_9AM, 30);
    }

    @Test
    void receiptAppearsOnlyAfterRealCommitWithCompleteSnapshot() {
        var summary = fixtures
            .transactionTemplate()
            .execute(status -> {
                var result = facade.book(userId, visitId, patientId);
                assertThat(inbox.find(userId)).isEmpty();
                return result;
            });
        assertThat(inbox.find(userId))
            .singleElement()
            .satisfies(receipt -> {
                assertThat(receipt.eventId()).isNotNull();
                assertThat(receipt.visitId()).isEqualTo(visitId);
                assertThat(receipt.dateTime()).isEqualTo(summary.dateTime());
                assertThat(receipt.zoneId()).isEqualTo("Europe/Warsaw");
                assertThat(receipt.durationInMinutes()).isEqualTo(30);
                assertThat(receipt.doctor()).isEqualTo(summary.doctor());
                assertThat(receipt.createdAt()).isEqualTo(FixedClockConfiguration.fixedClock().instant());
            });
        assertThat(inbox.find(fixtures.user())).isEmpty();
    }

    @Test
    void rollbackAfterPublicationLeavesNeitherBookingNorReceipt() {
        fixtures
            .transactionTemplate()
            .executeWithoutResult(status -> {
                facade.book(userId, visitId, patientId);
                assertThat(inbox.find(userId)).isEmpty();
                status.setRollbackOnly();
            });
        assertThat(visits.findById(visitId).orElseThrow().isAvailable()).isTrue();
        assertThat(inbox.find(userId)).isEmpty();
    }

    @Test
    void missingAndOccupiedSlotsDoNotProduceReceipts() {
        assertThatThrownBy(() -> facade.book(userId, -1L, patientId)).isInstanceOf(DomainException.class);
        assertThat(inbox.find(userId)).isEmpty();
        facade.book(userId, visitId, patientId);
        var before = inbox.find(userId);
        assertThatThrownBy(() -> facade.book(userId, visitId, patientId)).isInstanceOf(DomainException.class);
        assertThat(inbox.find(userId)).isEqualTo(before);
    }

    @Test
    void publicationWithoutTransactionIsIgnoredAndCommittedDuplicatesAreDeduplicated() {
        var event = new VisitBookedEvent(
            new UUID(0, 1),
            userId,
            visitId,
            FixedClockConfiguration.TOMORROW_9AM,
            "Europe/Warsaw",
            30,
            new DoctorSummaryDto(1L, "Jan Test", "Cardiology"),
            FixedClockConfiguration.fixedClock().instant()
        );
        publisher.publishEvent(event);
        assertThat(inbox.find(userId)).isEmpty();
        fixtures
            .transactionTemplate()
            .executeWithoutResult(status -> {
                publisher.publishEvent(event);
                publisher.publishEvent(event);
                assertThat(inbox.find(userId)).isEmpty();
            });
        assertThat(inbox.find(userId)).hasSize(1);
    }

    @Test
    void cancellationKeepsHistoryAndRebookingGetsNewIdentity() {
        facade.book(userId, visitId, patientId);
        var original = inbox.find(userId);
        facade.cancel(userId, visitId);
        assertThat(inbox.find(userId)).isEqualTo(original);
        facade.book(userId, visitId, patientId);
        assertThat(inbox.find(userId)).hasSize(2);
        assertThat(inbox.find(userId).getFirst().eventId()).isNotEqualTo(original.getFirst().eventId());
        assertThat(inbox.find(userId).getLast()).isEqualTo(original.getFirst());
    }

    @Test
    void channelFailurePreservesSuccessfulHttpResponseAndCommittedBooking() throws Exception {
        doThrow(new IllegalStateException("simulated channel failure")).when(inbox).add(eq(userId), any());
        mvc
            .perform(
                post("/api/users/{u}/visits/{v}/booking", userId, visitId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":" + patientId + "}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.visitId").value(visitId));
        assertThat(visits.findById(visitId).orElseThrow().isAvailable()).isFalse();
        assertThat(inbox.find(userId)).isEmpty();
    }

    @Test
    void endpointReturnsEmptyHistorySnapshotAndExistingErrorWrapper() throws Exception {
        String path = "/api/users/" + userId + "/booking-confirmations";
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc
            .perform(get("/api/users/-1/booking-confirmations"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        facade.book(userId, visitId, patientId);
        mvc
            .perform(get(path))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].visitId").value(visitId))
            .andExpect(jsonPath("$[0].eventId").isString())
            .andExpect(jsonPath("$[0].dateTime").value("2030-01-08T09:00:00"))
            .andExpect(jsonPath("$[0].zoneId").value("Europe/Warsaw"))
            .andExpect(jsonPath("$[0].createdAt").value("2030-01-07T07:00:00Z"))
            .andExpect(jsonPath("$[0].doctor.specialization").value("Cardiology"))
            .andExpect(jsonPath("$[0].patient").doesNotExist());
    }
}
