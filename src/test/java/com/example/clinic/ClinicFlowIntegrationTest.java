package com.example.clinic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.support.FixedClockConfiguration;
import com.example.clinic.support.TestFixtures;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Pełny przepływ przez HTTP, serwisy, transakcje i prawdziwą bazę (MockMvc + cały kontekst).
 * Ten sam scenariusz co w kolekcji Bruno: konto -> pacjent -> placówki -> doktor -> slot ->
 * rezerwacja -> konflikt -> anulowanie. Identyfikatory zawsze z odpowiedzi, nigdy "id=1".
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({ FixedClockConfiguration.class, TestFixtures.class })
class ClinicFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void fullBookingScenario_positiveAndNegativeSteps() throws Exception {
        long userId = id(
            postJson(
                "/api/users",
                """
                {"email":"%s","password":"Secret-123","role":"USER"}""".formatted(TestFixtures.uniqueEmail())
            ).andExpect(status().isCreated())
        );
        long patientId = id(
            postJson(
                "/api/patients",
                """
                {"firstName":"Anna","lastName":"Nowak","idCardNo":"%s","birthday":"1990-05-17","userId":%d}""".formatted(
                        TestFixtures.uniqueIdCard(),
                        userId
                    )
            ).andExpect(status().isCreated())
        );
        long north = id(postJson("/api/facilities", "{\"name\":\"North\",\"address\":\"Street 1\"}"));
        long south = id(postJson("/api/facilities", "{\"name\":\"South\",\"address\":\"Street 2\"}"));
        long doctorAccount = id(
            postJson(
                "/api/users",
                """
                {"email":"%s","password":"Secret-123","role":"USER"}""".formatted(TestFixtures.uniqueEmail())
            )
        );
        long doctorId = id(
            postJson(
                "/api/doctors",
                """
                {"firstName":"Jan","lastName":"Kowalski","specialization":"Cardiology","userId":%d,"facilityIds":[%d,%d]}""".formatted(
                        doctorAccount,
                        north,
                        south
                    )
            )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.facilities.length()").value(2))
        );

        // Drugi doktor dla tego samego konta: 409.
        postJson(
            "/api/doctors",
            """
            {"firstName":"X","lastName":"Y","specialization":"Z","userId":%d}""".formatted(doctorAccount)
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("USER_ALREADY_DOCTOR"));

        MvcResult slot = postJson("/api/visits", slotJson(doctorId, "2030-01-08T10:00:00", 30))
            .andExpect(status().isCreated())
            .andReturn();
        long visitId = ((Number) JsonPath.read(slot.getResponse().getContentAsString(), "$.id")).longValue();
        // Location wskazuje zasób, który da się odczytać.
        mvc
            .perform(get(slot.getResponse().getHeader("Location")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.patientId").doesNotExist());

        postJson("/api/visits", slotJson(doctorId, "2030-01-08T10:15:00", 30))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VISIT_OVERLAP"));
        postJson("/api/visits", slotJson(doctorId, "2030-01-08T10:30:00", 30)).andExpect(status().isCreated());
        postJson("/api/visits", slotJson(doctorId, "2030-01-07T07:00:00", 30))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VISIT_IN_PAST"));

        String bookingPath = "/api/users/%d/visits/%d/booking".formatted(userId, visitId);
        postJson(bookingPath, "{\"patientId\":%d}".formatted(patientId))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.patient.fullName").value("Anna Nowak"))
            .andExpect(jsonPath("$.doctor.fullName").value("Jan Kowalski"));
        postJson(bookingPath, "{\"patientId\":%d}".formatted(patientId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("VISIT_ALREADY_BOOKED"));
        mvc.perform(get(bookingPath)).andExpect(status().isOk()).andExpect(jsonPath("$.visitId").value(visitId));
        mvc
            .perform(get("/api/visits").param("patientId", String.valueOf(patientId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value(visitId));

        // Używane zasoby nie dają się usunąć.
        mvc.perform(delete("/api/visits/" + visitId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/users/" + userId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/patients/" + patientId)).andExpect(status().isConflict());
        mvc.perform(delete("/api/facilities/" + north)).andExpect(status().isConflict());

        // Anulowanie idempotentne.
        mvc.perform(delete(bookingPath)).andExpect(status().isNoContent());
        mvc.perform(delete(bookingPath)).andExpect(status().isNoContent());
        mvc.perform(get(bookingPath)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/visits/" + visitId)).andExpect(status().isNoContent());
    }

    @Test
    void bookingForPatientOfAnotherAccount_conflict() throws Exception {
        long owner = id(postJson("/api/users", userJson(TestFixtures.uniqueEmail())));
        long stranger = id(postJson("/api/users", userJson(TestFixtures.uniqueEmail())));
        long patientId = id(postJson("/api/patients", patientJson(owner, TestFixtures.uniqueIdCard())));
        long doctorId = id(
            postJson(
                "/api/doctors",
                """
                {"firstName":"A","lastName":"B","specialization":"C","userId":%d}""".formatted(
                        id(postJson("/api/users", userJson(TestFixtures.uniqueEmail())))
                    )
            )
        );
        long visitId = id(postJson("/api/visits", slotJson(doctorId, "2030-01-09T09:00:00", 15)));

        postJson(
            "/api/users/%d/visits/%d/booking".formatted(stranger, visitId),
            "{\"patientId\":%d}".formatted(patientId)
        )
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("OWNERSHIP_MISMATCH"));
    }

    @Test
    void uniqueness_emailNormalizedAndIdCard_conflicts() throws Exception {
        String email = TestFixtures.uniqueEmail();
        long userId = id(postJson("/api/users", userJson(email)));
        // Ten sam adres wielkimi literami: po normalizacji to duplikat.
        postJson("/api/users", userJson(email.toUpperCase(java.util.Locale.ROOT)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));

        String idCard = TestFixtures.uniqueIdCard();
        postJson("/api/patients", patientJson(userId, idCard)).andExpect(status().isCreated());
        postJson("/api/patients", patientJson(userId, idCard))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ID_CARD_ALREADY_EXISTS"));
    }

    @Test
    void putPatient_isIdempotentAndReplacesPhone() throws Exception {
        long userId = id(postJson("/api/users", userJson(TestFixtures.uniqueEmail())));
        String idCard = TestFixtures.uniqueIdCard();
        long patientId = id(postJson("/api/patients", patientJson(userId, idCard)));
        String body =
            """
            {"firstName":"Anna","lastName":"Kowalska","idCardNo":"%s","birthday":"1990-05-17"}""".formatted(idCard);

        for (int attempt = 0; attempt < 2; attempt++) {
            mvc
                .perform(put("/api/patients/" + patientId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Kowalska"))
                .andExpect(jsonPath("$.phoneNumber").doesNotExist());
        }
        // PUT bez wymaganego pola nie "zachowuje starej wartości" - to 400.
        mvc
            .perform(
                put("/api/patients/" + patientId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"firstName\":\"Anna\"}")
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void facilityAssignment_idempotentPutAndDelete() throws Exception {
        long facility = id(postJson("/api/facilities", "{\"name\":\"East\",\"address\":\"Street 9\"}"));
        long doctorId = id(
            postJson(
                "/api/doctors",
                """
                {"firstName":"A","lastName":"B","specialization":"C","userId":%d}""".formatted(
                        id(postJson("/api/users", userJson(TestFixtures.uniqueEmail())))
                    )
            )
        );
        String path = "/api/doctors/%d/facilities/%d".formatted(doctorId, facility);

        mvc.perform(put(path)).andExpect(status().isNoContent());
        mvc.perform(put(path)).andExpect(status().isNoContent());
        mvc.perform(get("/api/doctors/" + doctorId)).andExpect(jsonPath("$.facilities.length()").value(1));
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        mvc.perform(delete(path)).andExpect(status().isNoContent());
        mvc
            .perform(put("/api/doctors/%d/facilities/999999".formatted(doctorId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("FACILITY_NOT_FOUND"));
        // Doktor bez wizyt może zostać usunięty; placówka i konto zostają.
        mvc.perform(delete("/api/doctors/" + doctorId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/facilities/" + facility)).andExpect(status().isOk());
    }

    @Test
    void visitSearch_contradictoryRange_returns400() throws Exception {
        mvc
            .perform(get("/api/visits").param("from", "2030-01-09T00:00:00").param("to", "2030-01-08T00:00:00"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors[0].code").value("INVALID_RANGE"));
    }

    private ResultActions postJson(String path, String json) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static long id(ResultActions actions) throws Exception {
        return ((Number) JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    private static String userJson(String email) {
        return "{\"email\":\"%s\",\"password\":\"Secret-123\",\"role\":\"USER\"}".formatted(email);
    }

    private static String patientJson(long userId, String idCard) {
        return """
        {"firstName":"Ewa","lastName":"Test","idCardNo":"%s","birthday":"1990-05-17","userId":%d}""".formatted(
                idCard,
                userId
            );
    }

    private static String slotJson(long doctorId, String dateTime, int minutes) {
        return "{\"doctorId\":%d,\"dateTime\":\"%s\",\"durationInMinutes\":%d}".formatted(doctorId, dateTime, minutes);
    }
}
