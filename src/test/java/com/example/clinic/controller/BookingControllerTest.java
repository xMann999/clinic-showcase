package com.example.clinic.controller;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.dto.BookingSummaryDto;
import com.example.clinic.dto.DoctorSummaryDto;
import com.example.clinic.dto.PatientSummaryDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.facade.BookingFacade;
import com.example.clinic.support.WebSliceTestConfiguration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Kontroler rezerwacji zależy wyłącznie od fasady - w teście podmieniamy fasadę,
 * a nie jej wewnętrzne serwisy.
 */
@WebMvcTest(BookingController.class)
@Import(WebSliceTestConfiguration.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingFacade bookingFacade;

    private static BookingSummaryDto summary() {
        return new BookingSummaryDto(
            30L,
            LocalDateTime.of(2030, 1, 8, 10, 0),
            30,
            new PatientSummaryDto(10L, "Ewa Nowak"),
            new DoctorSummaryDto(20L, "Jan Kowalski", "Cardiology")
        );
    }

    @Test
    void post_booking_returns201WithNestedSummaryAndNoSensitiveFields() throws Exception {
        when(bookingFacade.book(1L, 30L, 10L)).thenReturn(summary());

        mockMvc
            .perform(
                post("/api/users/1/visits/30/booking")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":10}")
            )
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/users/1/visits/30/booking")))
            .andExpect(jsonPath("$.visitId").value(30))
            .andExpect(jsonPath("$.patient.fullName").value("Ewa Nowak"))
            .andExpect(jsonPath("$.patient.idCardNo").doesNotExist())
            .andExpect(jsonPath("$.patient.userId").doesNotExist())
            .andExpect(jsonPath("$.doctor.specialization").value("Cardiology"));
    }

    @Test
    void post_missingPatientId_returns400AndFacadeNotCalled() throws Exception {
        mockMvc
            .perform(post("/api/users/1/visits/30/booking").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("patientId"));
        verifyNoInteractions(bookingFacade);
    }

    @Test
    void post_alreadyBooked_returns409() throws Exception {
        when(bookingFacade.book(1L, 30L, 10L)).thenThrow(
            new DomainException(ErrorCode.VISIT_ALREADY_BOOKED, "Visit 30 is already booked")
        );

        mockMvc
            .perform(
                post("/api/users/1/visits/30/booking")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":10}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VISIT_ALREADY_BOOKED"));
    }

    @Test
    void post_versionConflict_returns409ConcurrentModification() throws Exception {
        when(bookingFacade.book(1L, 30L, 10L)).thenThrow(new OptimisticLockingFailureException("row was updated"));

        mockMvc
            .perform(
                post("/api/users/1/visits/30/booking")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"patientId\":10}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void get_noBooking_returns404() throws Exception {
        when(bookingFacade.get(1L, 30L)).thenThrow(new DomainException(ErrorCode.BOOKING_NOT_FOUND, "none"));

        mockMvc
            .perform(get("/api/users/1/visits/30/booking"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void delete_cancel_returns204() throws Exception {
        mockMvc.perform(delete("/api/users/1/visits/30/booking")).andExpect(status().isNoContent());
    }
}
