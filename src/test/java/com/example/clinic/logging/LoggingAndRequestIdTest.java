package com.example.clinic.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Logi i requestId w pełnym kontekście.
 *
 * Wyciek sprawdzamy po KONKRETNYCH fikcyjnych wartościach (hasło, numer dokumentu),
 * a nie po słowie "password", które może legalnie wystąpić w nazwie pola.
 * MockMvc wykonuje żądanie w wątku testu - widać więc, czy filtr posprzątał MDC.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class LoggingAndRequestIdTest {

    private static final String SECRET_PASSWORD = "Zz-Unique-Pass-4711";
    private static final String SECRET_ID_CARD = "ZQX987654";

    @Autowired
    private MockMvc mvc;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sensitiveValues_neverAppearInLogs(CapturedOutput output) throws Exception {
        MvcResult user = mvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"%s\",\"role\":\"USER\"}".formatted(
                                TestFixtures.uniqueEmail(),
                                SECRET_PASSWORD
                            )
                    )
            )
            .andExpect(status().isCreated())
            .andReturn();
        long userId =
            ((Number) com.jayway.jsonpath.JsonPath.read(user.getResponse().getContentAsString(), "$.id")).longValue();
        mvc
            .perform(
                post("/api/patients")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"firstName":"A","lastName":"B","idCardNo":"%s","birthday":"1990-01-01","userId":%d}""".formatted(
                                SECRET_ID_CARD,
                                userId
                            )
                    )
            )
            .andExpect(status().isCreated());
        // Odrzucone żądanie (zbyt krótkie hasło) również nie może wypisać wartości.
        mvc
            .perform(
                post("/api/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"x@example.test\",\"password\":\"Qw-71\",\"role\":\"USER\"}")
            )
            .andExpect(status().isBadRequest());

        assertThat(output.getAll())
            .contains("Created user", "Created patient")
            .doesNotContain(SECRET_PASSWORD, SECRET_ID_CARD, "Qw-71");
    }

    @Test
    void requestId_validClientValueEchoedInvalidReplaced() throws Exception {
        MvcResult accepted = mvc
            .perform(get("/api/users/999999").header(RequestIdFilter.HEADER, "client-abc-12345"))
            .andExpect(status().isNotFound())
            .andReturn();
        assertThat(accepted.getResponse().getHeader(RequestIdFilter.HEADER)).isEqualTo("client-abc-12345");
        assertThat(accepted.getResponse().getContentAsString()).contains("\"requestId\":\"client-abc-12345\"");

        MvcResult replaced = mvc
            .perform(get("/api/users/999999").header(RequestIdFilter.HEADER, "bad value\r\nINJECTED"))
            .andReturn();
        assertThat(replaced.getResponse().getHeader(RequestIdFilter.HEADER))
            .matches("[0-9a-f-]{36}")
            .doesNotContain("INJECTED");
    }

    @Test
    void mdc_cleanedAfterRequestAndPreviousValueRestored(CapturedOutput output) throws Exception {
        MvcResult first = mvc.perform(get("/api/users/999998")).andReturn();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();

        MDC.put(RequestIdFilter.MDC_KEY, "outer-context");
        MvcResult second = mvc.perform(get("/api/users/999997")).andReturn();
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo("outer-context");

        String firstId = first.getResponse().getHeader(RequestIdFilter.HEADER);
        String secondId = second.getResponse().getHeader(RequestIdFilter.HEADER);
        assertThat(firstId).isNotEqualTo(secondId);
        // Linia logu advice dla drugiego żądania niesie JEGO requestId (wzorzec logging.pattern.level).
        assertThat(output.getAll()).contains("requestId=" + secondId);
    }
}
