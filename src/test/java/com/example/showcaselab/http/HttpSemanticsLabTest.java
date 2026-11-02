package com.example.showcaselab.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.ClinicShowcaseApplication;
import com.example.clinic.support.FixedClockConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Bezpieczeństwo i idempotencja metod HTTP na prawdziwym API przychodni.
 * <h2>Cel</h2>
 * Odróżnić bezpieczeństwo od idempotencji na podstawie stanu zasobów po powtórzonych żądaniach.
 * <h2>Jak działa przykład</h2>
 * - GET jest bezpieczny: nie zmienia stanu serwera;
 * - PUT jest idempotentny: N powtórzeń = ten sam STAN co jedno wywołanie;
 * - DELETE jest idempotentny co do stanu, ale status odpowiedzi może się różnić (204, potem 404);
 * - POST nie jest idempotentny: powtórzenie tworzy drugi zasób. Jeśli odpowiedź na POST
 *   zaginie po commit, klient nie wie, czy ponowić - rozwiązaniem bywa nagłówek
 *   Idempotency-Key zapamiętywany przez serwer (opisany w docs/http-contract.md, nie wdrożony).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>GET nie zmienia stanu zasobu.</li>
 * <li>Powtórzony PUT pozostawia ten sam stan.</li>
 * <li>Powtórzony DELETE zmienia status, lecz nie stan końcowy.</li>
 * <li>Powtórzony POST tworzy osobny zasób.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Idempotencja dotyczy stanu końcowego, nie identycznego statusu odpowiedzi.
 */
@SpringBootTest(classes = ClinicShowcaseApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfiguration.class)
@DisplayName("Bezpieczeństwo i idempotencja metod HTTP na prawdziwym API przychodni.")
class HttpSemanticsLabTest {

    @Autowired
    private MockMvc mvc;

    private long createFacility() throws Exception {
        String body = mvc
            .perform(
                post("/api/facilities")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Lab\",\"address\":\"Street 1\"}")
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long totalFacilities() throws Exception {
        String body = mvc.perform(get("/api/facilities")).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.totalElements")).longValue();
    }

    /**
     * GET nie zmienia stanu zasobu.
     */
    @Test
    @DisplayName("GET nie zmienia stanu zasobu")
    void shouldPreserveStateWhenGetIsRepeated() throws Exception {
        long id = createFacility();
        long before = totalFacilities();

        mvc.perform(get("/api/facilities/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/facilities/" + id)).andExpect(status().isOk());

        assertThat(totalFacilities()).isEqualTo(before);
    }

    /**
     * Powtórzony PUT pozostawia ten sam stan.
     */
    @Test
    @DisplayName("Powtórzony PUT pozostawia ten sam stan")
    void shouldPreserveFinalStateWhenPutIsRepeated() throws Exception {
        long id = createFacility();
        String body = "{\"name\":\"Renamed\",\"address\":\"Street 2\"}";

        String first = mvc
            .perform(put("/api/facilities/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.address").value("Street 2"))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String second = mvc
            .perform(put("/api/facilities/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"))
            .andExpect(jsonPath("$.address").value("Street 2"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        var mapper = tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(mapper.readTree(second)).isEqualTo(mapper.readTree(first));
        mvc
            .perform(get("/api/facilities/" + id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed"));
    }

    /**
     * Powtórzony DELETE zmienia status, lecz nie stan końcowy.
     */
    @Test
    @DisplayName("Powtórzony DELETE zmienia status, lecz nie stan końcowy")
    void shouldKeepResourceAbsentWhenDeleteIsRepeated() throws Exception {
        long id = createFacility();

        mvc.perform(delete("/api/facilities/" + id)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/facilities/" + id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/facilities/" + id)).andExpect(status().isNotFound());
    }

    /**
     * Powtórzony POST tworzy osobny zasób.
     */
    @Test
    @DisplayName("Powtórzony POST tworzy osobny zasób")
    void shouldCreateDistinctResourcesWhenPostIsRepeated() throws Exception {
        long before = totalFacilities();
        long first = createFacility();
        long second = createFacility();

        assertThat(second).isNotEqualTo(first);
        assertThat(totalFacilities()).isEqualTo(before + 2);
    }
}
