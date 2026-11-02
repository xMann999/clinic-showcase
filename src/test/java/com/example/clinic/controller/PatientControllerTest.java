package com.example.clinic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.command.PatientCreateCommand;
import com.example.clinic.command.PatientUpdateCommand;
import com.example.clinic.dto.PatientDto;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.logging.RequestIdFilter;
import com.example.clinic.service.PatientService;
import com.example.clinic.support.WebSliceTestConfiguration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Wycinek MVC: prawdziwy DispatcherServlet, konwertery JSON, walidacja, advice i filtr
 * requestId; serwis jest dublerem (@MockitoBean), bazy nie ma.
 *
 * MockMvc nie otwiera gniazda sieciowego - zachowanie prawdziwego serwera HTTP sprawdza
 * osobny test na losowym porcie (HttpSmokeIT).
 */
@WebMvcTest(PatientController.class)
@Import(WebSliceTestConfiguration.class)
class PatientControllerTest {

    private static final String VALID_BODY =
        """
        {"firstName":"Anna","lastName":"Nowak","idCardNo":"ABC123456",
         "birthday":"1990-05-17","phoneNumber":"+48600700800","userId":7}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PatientService patientService;

    private static PatientDto dto() {
        return new PatientDto(11L, "Anna", "Nowak", "Anna Nowak", LocalDate.of(1990, 5, 17), "+48600700800", 7L);
    }

    @Test
    void post_validPatient_returns201WithLocationAndContractWithoutIdCard() throws Exception {
        when(patientService.create(any(PatientCreateCommand.class))).thenReturn(dto());

        mockMvc
            .perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", endsWith("/api/patients/11")))
            .andExpect(jsonPath("$.fullName").value("Anna Nowak"))
            .andExpect(jsonPath("$.birthday").value("1990-05-17"))
            .andExpect(jsonPath("$.userId").value(7))
            .andExpect(jsonPath("$.idCardNo").doesNotExist())
            .andExpect(jsonPath("$.password").doesNotExist())
            .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void getAndPut_neverExposeIdCard() throws Exception {
        when(patientService.get(11L)).thenReturn(dto());
        when(patientService.update(eq(11L), any(PatientUpdateCommand.class))).thenReturn(dto());

        mockMvc
            .perform(get("/api/patients/11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idCardNo").doesNotExist());
        mockMvc
            .perform(
                put("/api/patients/11")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"firstName":"Anna","lastName":"Nowak","idCardNo":"ABC123456","birthday":"1990-05-17"}
                        """
                    )
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idCardNo").doesNotExist());
    }

    @Test
    void post_invalidFields_returns400WithSortedViolationsAndServiceNotCalled() throws Exception {
        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"firstName":"","lastName":"Nowak","idCardNo":"123","birthday":"2999-01-01","userId":7}
                        """
                    )
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.path").value("/api/patients"))
            .andExpect(jsonPath("$.errors[0].field").value("birthday"))
            .andExpect(jsonPath("$.errors[0].code").value("Past"))
            .andExpect(jsonPath("$.errors[1].field").value("firstName"))
            .andExpect(jsonPath("$.errors[2].field").value("idCardNo"));
        // Walidacja zatrzymała żądanie PRZED serwisem.
        verifyNoInteractions(patientService);
    }

    @Test
    void post_malformedJson_returns400InvalidRequestWithoutEchoingBody() throws Exception {
        MvcResult result = mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"SECRET-VALUE\",}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("SECRET-VALUE");
        verifyNoInteractions(patientService);
    }

    @Test
    void post_unknownField_returns400UnknownField() throws Exception {
        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY.replace("\"userId\":7", "\"userId\":7,\"fistName\":\"typo\""))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("fistName"))
            .andExpect(jsonPath("$.errors[0].code").value("UNKNOWN_FIELD"));
    }

    @Test
    void post_wrongDateFormat_returns400WithFieldPath() throws Exception {
        mockMvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_BODY.replace("1990-05-17", "17.05.1990"))
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("birthday"))
            .andExpect(jsonPath("$.errors[0].code").value("INVALID_FORMAT"));
    }

    @Test
    void post_textPlain_returns415() throws Exception {
        mockMvc
            .perform(post("/api/patients").contentType(MediaType.TEXT_PLAIN).content("hello"))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void get_acceptXmlOnly_returns406() throws Exception {
        when(patientService.get(11L)).thenReturn(dto());

        mockMvc
            .perform(get("/api/patients/11").accept(MediaType.APPLICATION_XML))
            .andExpect(status().isNotAcceptable());
    }

    @Test
    void patch_unsupportedMethod_returns405WithAllowHeader() throws Exception {
        mockMvc
            .perform(patch("/api/patients/11").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(header().string("Allow", containsString("PUT")))
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void get_idNotANumber_returns400TypeMismatch() throws Exception {
        mockMvc
            .perform(get("/api/patients/abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("id"))
            .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"));
    }

    @Test
    void get_missingPatient_returns404WithStableCode() throws Exception {
        when(patientService.get(404L)).thenThrow(
            DomainException.notFound(ErrorCode.PATIENT_NOT_FOUND, "Patient", 404L)
        );

        mockMvc
            .perform(get("/api/patients/404"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PATIENT_NOT_FOUND"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    void delete_patientWithVisits_returns409AndSuccessReturnsEmpty204() throws Exception {
        doThrow(new DomainException(ErrorCode.RESOURCE_IN_USE, "Patient 1 has visits")).when(patientService).delete(1L);

        mockMvc
            .perform(delete("/api/patients/1"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));
        mockMvc.perform(delete("/api/patients/2")).andExpect(status().isNoContent()).andExpect(content().string(""));
    }

    @Test
    void unexpectedFailure_returns500WithoutLeakingDetailsAndWithRequestId() throws Exception {
        when(patientService.get(5L)).thenThrow(new IllegalStateException("SQL table PATIENTS password=topsecret"));

        MvcResult result = mockMvc
            .perform(get("/api/patients/5").header(RequestIdFilter.HEADER, "trace-12345678"))
            .andExpect(status().isInternalServerError())
            .andExpect(header().string(RequestIdFilter.HEADER, "trace-12345678"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").value("trace-12345678"))
            .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("topsecret", "PATIENTS", "IllegalState");
    }

    @Test
    void unknownPath_returns404NotFoundInApiErrorShape() throws Exception {
        mockMvc
            .perform(get("/api/does-not-exist"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void list_sizeAbove100_returns400InsteadOfSilentClamp() throws Exception {
        mockMvc
            .perform(get("/api/patients").param("size", "500"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("size"));
        mockMvc
            .perform(get("/api/patients").param("page", "-1"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].field").value("page"));
        mockMvc
            .perform(get("/api/patients").param("sort", "idCardNo,asc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_SORT_PROPERTY"));
        verifyNoInteractions(patientService);
    }

    @Test
    void list_repeatedSort_preservesPriorityAndAddsIdentifierTieBreaker() throws Exception {
        var pageable = PageRequest.of(2, 7, Sort.by(Sort.Order.desc("lastName"), Sort.Order.asc("id")));
        when(patientService.search(null, null, pageable))
            .thenReturn(new PageResponse<>(List.of(), 2, 7, 0, 0));

        mockMvc
            .perform(get("/api/patients").param("page", " 2 ").param("size", "7")
                .param("sort", " ", " lastName , DeSc "))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(2))
            .andExpect(jsonPath("$.size").value(7));

        verify(patientService).search(null, null, pageable);
    }

    @Test
    void list_explicitIdentifierSort_retainsItsDirectionAndPosition() throws Exception {
        var pageable = PageRequest.of(0, 20, Sort.by(Sort.Order.desc("id"), Sort.Order.asc("lastName")));
        when(patientService.search(null, null, pageable))
            .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc
            .perform(get("/api/patients").param("page", " ").param("size", " ")
                .param("sort", "id,desc", "lastName"))
            .andExpect(status().isOk());

        verify(patientService).search(null, null, pageable);
    }

    @Test
    void list_invalidPagingAndRepeatedSort_returnsAllViolationsInRequestOrder() throws Exception {
        mockMvc
            .perform(get("/api/patients").param("page", "bad").param("size", "0")
                .param("sort", "lastName,sideways", "id,asc,extra", "lastName,desc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.errors.length()").value(4))
            .andExpect(jsonPath("$.errors[0].field").value("page"))
            .andExpect(jsonPath("$.errors[0].code").value("TYPE_MISMATCH"))
            .andExpect(jsonPath("$.errors[1].field").value("size"))
            .andExpect(jsonPath("$.errors[1].code").value("Range"))
            .andExpect(jsonPath("$.errors[2].code").value("INVALID_SORT"))
            .andExpect(jsonPath("$.errors[2].message").value("direction must be asc or desc"))
            .andExpect(jsonPath("$.errors[3].code").value("INVALID_SORT"))
            .andExpect(jsonPath("$.errors[3].message").value("use sort=property or sort=property,asc|desc"));

        verifyNoInteractions(patientService);
    }

    @Test
    void patchPath_onUsersIsNotPatientConcern() throws Exception {
        // Kontroler kont nie jest w tym wycinku - brak handlera to 404, a nie 500.
        mockMvc
            .perform(patch("/api/users/1/password").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }
}
