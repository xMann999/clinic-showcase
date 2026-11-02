package com.example.showcaselab.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pięć rodzajów dublerów testowych na jednej małej usłudze.
 * <h2>Cel</h2>
 * Odróżnić przygotowaną odpowiedź, uproszczoną implementację i obserwację współpracy z zależnością.
 * <h2>Jak działa przykład</h2>
 * - dummy: przekazany tylko po to, by spełnić sygnaturę; nigdy nieużywany;
 * - stub: zwraca przygotowane odpowiedzi (sprawdzamy WYNIK testowanego kodu);
 * - fake: działająca, uproszczona implementacja (mapa zamiast bazy);
 * - mock: sprawdzamy INTERAKCJĘ (czy i jak został wywołany);
 * - spy: prawdziwy obiekt z możliwością podglądu wywołań.
 * Stubowanie i weryfikacja to dwie różne rzeczy - nie weryfikujemy tego, co tylko stubujemy.
 * JUnit Jupiter jest frameworkiem uruchamiania testów; Mockito - biblioteką dublerów.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Stub dostarcza odpowiedź sprawdzaną przez wynik.</li>
 * <li>Mock sprawdza powiadomienie po zapisaniu zmiany.</li>
 * <li>Odrzucona operacja nie wysyła powiadomienia.</li>
 * <li>Spy wykonuje rzeczywistą operację i rejestruje wywołanie.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Mockito nie jest kontenerem Springa. Fake nie dowodzi zachowania prawdziwej bazy danych.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Pięć rodzajów dublerów testowych na jednej małej usłudze.")
class TestDoublesLabTest {

    interface PatientDirectory {
        Optional<String> findName(String id);

        void save(String id, String name);
    }

    interface Notifier {
        void notify(String message);
    }

    interface AuditLog {
        void record(String entry);
    }

    record RenameService(PatientDirectory directory, Notifier notifier, AuditLog audit) {
        boolean rename(String id, String newName) {
            if (directory.findName(id).isEmpty()) {
                return false;
            }
            directory.save(id, newName);
            notifier.notify("renamed " + id);
            return true;
        }
    }

    static final class FakeDirectory implements PatientDirectory {

        final Map<String, String> data = new HashMap<>();

        public Optional<String> findName(String id) {
            return Optional.ofNullable(data.get(id));
        }

        public void save(String id, String name) {
            data.put(id, name);
        }
    }

    static final class RecordingNotifier implements Notifier {

        final List<String> messages = new ArrayList<>();

        public void notify(String message) {
            messages.add(message);
        }
    }

    @Mock
    private Notifier mockNotifier;

    private final AuditLog dummyAudit = entry -> {
        throw new AssertionError("dummy must not be used");
    };

    /**
     * Stub dostarcza odpowiedź sprawdzaną przez wynik.
     */
    @Test
    @DisplayName("Stub dostarcza odpowiedź sprawdzaną przez wynik")
    void shouldReturnFalseWhenStubReportsMissingPatient() {
        PatientDirectory stub = mock(PatientDirectory.class);
        when(stub.findName("1")).thenReturn(Optional.empty());

        assertThat(new RenameService(stub, mockNotifier, dummyAudit).rename("1", "X")).isFalse();
    }

    /**
     * Mock sprawdza powiadomienie po zapisaniu zmiany.
     */
    @Test
    @DisplayName("Mock sprawdza powiadomienie po zapisaniu zmiany")
    void shouldNotifyWhenRenameSucceeds() {
        FakeDirectory fake = new FakeDirectory();
        fake.save("1", "Old");

        new RenameService(fake, mockNotifier, dummyAudit).rename("1", "New");

        verify(mockNotifier).notify("renamed 1");
        assertThat(fake.data).containsEntry("1", "New");
    }

    /**
     * Odrzucona operacja nie wysyła powiadomienia.
     */
    @Test
    @DisplayName("Odrzucona operacja nie wysyła powiadomienia")
    void shouldAvoidNotificationWhenPatientIsMissing() {
        FakeDirectory directory = new FakeDirectory();
        assertThat(new RenameService(directory, mockNotifier, dummyAudit).rename("missing", "X")).isFalse();
        assertThat(directory.data).isEmpty();

        verify(mockNotifier, never()).notify(anyString());
    }

    /**
     * Spy wykonuje rzeczywistą operację i rejestruje wywołanie.
     */
    @Test
    @DisplayName("Spy wykonuje rzeczywistą operację i rejestruje wywołanie")
    void shouldRecordRealMessageWhenNotifierIsSpied() {
        RecordingNotifier spyNotifier = spy(new RecordingNotifier());
        FakeDirectory fake = new FakeDirectory();
        fake.save("2", "A");

        new RenameService(fake, spyNotifier, dummyAudit).rename("2", "B");

        verify(spyNotifier).notify("renamed 2");
        assertThat(spyNotifier.messages).containsExactly("renamed 2");
    }
}
