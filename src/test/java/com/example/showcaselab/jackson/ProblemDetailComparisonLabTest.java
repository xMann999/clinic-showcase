package com.example.showcaselab.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.error.ApiError;
import com.example.clinic.error.FieldViolation;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * ProblemDetail (RFC 9457) kontra własny ApiError.
 * <h2>Cel</h2>
 * Porównać status, opis, kategorię i szczegóły błędu w dwóch modelach odpowiedzi.
 * <h2>Jak działa przykład</h2>
 * ProblemDetail to standardowa koperta błędu: type (URI kategorii), title, status, detail,
 * instance (URI konkretnego wystąpienia) i dowolne rozszerzenia (setProperty).
 * Projekt świadomie używa własnego ApiError: stabilne pole "code", requestId i lista
 * errors są częścią kontraktu, a nie opcjonalnym rozszerzeniem. Publiczne API zwraca
 * wyłącznie ApiError - ProblemDetail istnieje tylko tutaj, do porównania.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Ten sam błąd ma dwa różne kontrakty JSON.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Test porównuje pola obiektów. Nie sprawdza serializacji ani konfiguracji konwertera HTTP.
 */
@DisplayName("ProblemDetail (RFC 9457) kontra własny ApiError.")
class ProblemDetailComparisonLabTest {

    /**
     * Ten sam błąd ma dwa różne kontrakty JSON.
     * Kod maszynowy w ProblemDetail niesie "type" (URI); w ApiError - krótki "code".
     */
    @Test
    @DisplayName("Ten sam błąd ma dwa różne kontrakty JSON")
    void shouldRepresentSameProblemWhenUsingDifferentErrorModels() {
        List<FieldViolation> violations = List.of(new FieldViolation("lastName", "NotBlank", "must not be blank"));

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setType(URI.create("https://clinic.example.test/problems/validation-failed"));
        problem.setTitle("Validation failed");
        problem.setInstance(URI.create("/api/patients"));
        problem.setProperty("errors", violations);

        ApiError apiError = new ApiError(
            Instant.parse("2030-01-07T07:00:00Z"),
            400,
            "VALIDATION_FAILED",
            "Request validation failed",
            "/api/patients",
            "req-12345678",
            violations
        );

        assertThat(problem.getStatus()).isEqualTo(apiError.status());
        assertThat(problem.getDetail()).isEqualTo(apiError.message());
        assertThat(problem.getInstance()).hasToString(apiError.path());
        assertThat(problem.getProperties()).containsEntry("errors", apiError.errors());

        assertThat(problem.getType().getPath()).endsWith("validation-failed");
        assertThat(apiError.code()).isEqualTo("VALIDATION_FAILED");
    }
}
