package com.example.showcaselab.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Konfiguracja logback - appendery, poziomy, wzorzec z MDC i zapis JSON.
 * <h2>Cel</h2>
 * Powiązać poziom loggera, appender i MDC z rzeczywistą zawartością plików logów.
 * <h2>Jak działa przykład</h2>
 * Plik: src/test/resources/labs/logging/logback-lab.xml
 * Tworzymy OSOBNY LoggerContext, aby nie zmieniać konfiguracji logów aplikacji i innych testów.
 * Placeholdery {} SLF4J: tekst komunikatu powstaje tylko, gdy poziom jest włączony.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Poziomy i MDC są widoczne w osobnych logach tekstowych i JSON.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Osobny LoggerContext nie zmienia konfiguracji logów aplikacji.
 */
@DisplayName("Konfiguracja logback - appendery, poziomy, wzorzec z MDC i zapis JSON.")
class LogbackLabTest {

    @TempDir
    Path dir;

    /**
     * Poziomy i MDC są widoczne w osobnych logach tekstowych i JSON.
     * Osobny LoggerContext ma WŁASNY adapter MDC. Kod aplikacji używa org.slf4j.MDC, które
     * trafia do domyślnego kontekstu; tutaj wpisujemy wartość bezpośrednio do adaptera labu.
     * JsonEncoder zapisuje log STRUKTURALNY: szablon komunikatu ("Created visit {}") i argumenty
     * osobno, a MDC jako obiekt. System zbierający logi może wtedy filtrować po polach
     * (np. wszystkie zdarzenia danego requestId) bez parsowania tekstu.
     */
    @Test
    @DisplayName("Poziomy i MDC są widoczne w osobnych logach tekstowych i JSON")
    void shouldWriteFilteredEventsWithMdcWhenLoggingToBothAppenders() throws Exception {
        LoggerContext context = new LoggerContext();
        LogbackMDCAdapter mdc = new LogbackMDCAdapter();
        context.setMDCAdapter(mdc);
        try {
            context.start();
            context.putProperty("LOG_DIR", dir.toString());
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(LogbackLabTest.class.getResource("/labs/logging/logback-lab.xml"));

            Logger clinic = context.getLogger("lab.clinic.VisitService");
            Logger framework = context.getLogger("lab.framework");
            mdc.put("requestId", "req-lab-1");
            clinic.info("Created visit {}", 7);
            clinic.debug("Overlap check for doctor {} returned {}", 3, false);
            framework.debug("framework detail hidden at INFO");
        } finally {
            mdc.remove("requestId");
            context.stop();
        }

        String text = Files.readString(dir.resolve("lab.log"));
        String json = Files.readString(dir.resolve("lab.json"));

        assertThat(text)
            .contains("[requestId=req-lab-1]", "Created visit 7", "Overlap check for doctor 3 returned false")
            .doesNotContain("framework detail hidden");

        assertThat(json).contains("\"level\":\"INFO\"", "Created visit {}", "\"7\"", "\"requestId\":\"req-lab-1\"");
    }
}
