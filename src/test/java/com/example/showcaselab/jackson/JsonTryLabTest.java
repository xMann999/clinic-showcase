package com.example.showcaselab.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.vavr.control.Try;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jeden, mały przykład Vavr Try przy odczycie pliku JSON.
 * <h2>Cel</h2>
 * Zamienić błąd adaptera plikowego na wyjątek domenowy bez utraty pierwotnej przyczyny.
 * <h2>Jak działa przykład</h2>
 * Files.readString rzuca SPRAWDZANY IOException - w lambdzie (np. w strumieniu) trzeba by go
 * łapać ręcznie. Try.of przyjmuje kod rzucający wyjątki sprawdzane i zamienia wynik na wartość:
 * Success albo Failure. Parsowanie przez Jacksona 3 rzuca wyjątki NIESPRAWDZANE - Try łapie je tak samo.
 * Na granicy adaptera zamieniamy Failure na własny wyjątek, ZACHOWUJĄC cause.
 * Pułapka: nie rozprowadzamy Try po serwisach i API; nie używamy recover(), które zamieniłoby
 * błąd w pozorny sukces (np. pustego pacjenta).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Poprawny plik daje sparsowany rekord.</li>
 * <li>Brak pliku zachowuje przyczynę błędu wejścia.</li>
 * <li>Błędny JSON zachowuje przyczynę parsowania.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Używany jest Jackson 3 z pakietów tools.jackson; adnotacje pochodzą z com.fasterxml.jackson.annotation. Konfiguracja lokalnego mappera nie jest konfiguracją Boota.
 */
@DisplayName("Jeden, mały przykład Vavr Try przy odczycie pliku JSON.")
class JsonTryLabTest {

    record ImportedPatient(String firstName, LocalDate birthday) {}

    static class PatientImportException extends RuntimeException {

        PatientImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static final class PatientFileReader {

        private final JsonMapper mapper = JsonMapper.builder().build();

        ImportedPatient read(Path file) {
            return Try.of(() -> Files.readString(file))
                .mapTry(text -> mapper.readValue(text, ImportedPatient.class))
                .getOrElseThrow(cause ->
                    new PatientImportException("Cannot import patient file " + file.getFileName(), cause)
                );
        }
    }

    @TempDir
    Path dir;

    private final PatientFileReader reader = new PatientFileReader();

    /**
     * Poprawny plik daje sparsowany rekord.
     */
    @Test
    @DisplayName("Poprawny plik daje sparsowany rekord")
    void shouldReturnRecordWhenFileIsValid() throws IOException {
        Path file = Files.writeString(dir.resolve("ok.json"), "{\"firstName\":\"Anna\",\"birthday\":\"1990-05-17\"}");

        assertThat(reader.read(file)).isEqualTo(new ImportedPatient("Anna", LocalDate.of(1990, 5, 17)));
    }

    /**
     * Brak pliku zachowuje przyczynę błędu wejścia.
     */
    @Test
    @DisplayName("Brak pliku zachowuje przyczynę błędu wejścia")
    void shouldPreserveIoCauseWhenFileIsMissing() {
        assertThatThrownBy(() -> reader.read(dir.resolve("missing.json")))
            .isInstanceOf(PatientImportException.class)
            .hasCauseInstanceOf(NoSuchFileException.class);
    }

    /**
     * Błędny JSON zachowuje przyczynę parsowania.
     */
    @Test
    @DisplayName("Błędny JSON zachowuje przyczynę parsowania")
    void shouldPreserveParsingCauseWhenJsonIsMalformed() throws IOException {
        Path file = Files.writeString(dir.resolve("bad.json"), "{\"firstName\":");

        assertThatThrownBy(() -> reader.read(file))
            .isInstanceOf(PatientImportException.class)
            .hasCauseInstanceOf(StreamReadException.class);
    }
}
