package com.example.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.command.VisitCreateCommand;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.VisitDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.service.VisitService;
import com.example.clinic.support.WebSliceTestConfiguration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(VisitController.class)
@Import(WebSliceTestConfiguration.class)
class VisitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VisitService visitService;

    @Test
    void post_notQuarterHourAndBadDuration_returns400FromCustomConstraint() throws Exception {
        mockMvc
            .perform(
                post("/api/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"doctorId\":1,\"dateTime\":\"2030-01-08T10:10:00\",\"durationInMinutes\":20}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("dateTime"))
            .andExpect(jsonPath("$.errors[0].code").value("QuarterHour"))
            .andExpect(jsonPath("$.errors[1].field").value("durationInMinutes"))
            .andExpect(jsonPath("$.errors[1].code").value("QuarterHour"));
        verifyNoInteractions(visitService);
    }

    @Test
    void post_missingDateTime_onlyNotNullReported() throws Exception {
        mockMvc
            .perform(
                post("/api/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"doctorId\":1,\"durationInMinutes\":30}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].code").value("NotNull"));
    }

    @Test
    void post_valid_returns201AndLocalDateTimeWithoutOffset() throws Exception {
        LocalDateTime start = LocalDateTime.of(2030, 1, 8, 10, 15);
        when(visitService.createSlot(any(VisitCreateCommand.class))).thenReturn(new VisitDto(3L, 1L, null, start, 30));

        mockMvc
            .perform(
                post("/api/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"doctorId\":1,\"dateTime\":\"2030-01-08T10:15:00\",\"durationInMinutes\":30}")
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.dateTime").value("2030-01-08T10:15:00"))
            .andExpect(jsonPath("$.patientId").doesNotExist());
    }

    @Test
    void post_overlap_returns409() throws Exception {
        when(visitService.createSlot(any())).thenThrow(new DomainException(ErrorCode.VISIT_OVERLAP, "overlap"));

        mockMvc
            .perform(
                post("/api/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"doctorId\":1,\"dateTime\":\"2030-01-08T10:15:00\",\"durationInMinutes\":30}")
            )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VISIT_OVERLAP"));
    }

    @Test
    void post_inPast_returns400VisitInPast() throws Exception {
        when(visitService.createSlot(any())).thenThrow(new DomainException(ErrorCode.VISIT_IN_PAST, "past"));

        mockMvc
            .perform(
                post("/api/visits")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"doctorId\":1,\"dateTime\":\"2020-01-08T10:15:00\",\"durationInMinutes\":30}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VISIT_IN_PAST"));
    }

    @Test
    void search_defaultSort_isDateTimeThenId() throws Exception {
        when(visitService.search(any(), any())).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc
            .perform(get("/api/visits").param("doctorId", "1").param("from", "2030-01-08T00:00:00"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(visitService).search(any(), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("dateTime").and(Sort.by("id")));
    }

    @Test
    void search_malformedFromDate_returns400() throws Exception {
        mockMvc
            .perform(get("/api/visits").param("from", "tomorrow"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("from"));
    }
}
