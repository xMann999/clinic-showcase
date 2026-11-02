package com.example.showcaselab.mvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Droga żądania przez Spring MVC na małym, niezależnym kontrolerze.
 * <h2>Cel</h2>
 * Powiązać odczyt żądania, resolver argumentów i odpowiedź z wywołaniem rzeczywistego kontrolera.
 * <h2>Jak działa przykład</h2>
 * DispatcherServlet przyjmuje żądanie -> HandlerMapping wybiera metodę po URL i metodzie HTTP ->
 * HandlerMethodArgumentResolver-y przygotowują argumenty (nagłówek, body przez HttpMessageConverter
 * z Jacksonem, Pageable...) -> metoda kontrolera -> konwerter zapisuje wynik.
 * Jeśli body nie da się odczytać, metoda (i serwis) w ogóle nie zostaje wywołana.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Poprawny JSON dociera do usługi i zwraca status utworzenia.</li>
 * <li>Błędny JSON zatrzymuje przetwarzanie przed kontrolerem.</li>
 * <li>Nagłówek i jego brak dają odpowiednie argumenty.</li>
 * <li>Domyślny resolver koryguje błędne parametry strony.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Magazyn pamięciowy nie ma trwałości ani transakcji. Standalone MockMvc nie uruchamia całej konfiguracji aplikacji.
 */
@DisplayName("Droga żądania przez Spring MVC na małym, niezależnym kontrolerze.")
class MvcPipelineLabTest {

    record NoteCommand(String text) {}

    static final class RecordingService {

        final List<String> calls = new ArrayList<>();

        String save(String text) {
            calls.add(text);
            return text.toUpperCase(java.util.Locale.ROOT);
        }
    }

    /** Wariant @Controller + @ResponseBody zamiast @RestController; status przez @ResponseStatus. */
    @Controller
    static final class LabController {

        private final RecordingService service;

        LabController(RecordingService service) {
            this.service = service;
        }

        @PostMapping("/lab/notes")
        @ResponseStatus(HttpStatus.CREATED)
        @ResponseBody
        NoteCommand create(@RequestBody NoteCommand command) {
            return new NoteCommand(service.save(command.text()));
        }

        /** @RequestHeader: wartość z nagłówka HTTP; tu - opcjonalna. */
        @GetMapping("/lab/whoami")
        @ResponseBody
        String whoAmI(@RequestHeader(name = "X-Client", required = false, defaultValue = "unknown") String client) {
            return "client=" + client;
        }

        @GetMapping("/lab/page")
        @ResponseBody
        String page(Pageable pageable) {
            return pageable.getPageNumber() + "/" + pageable.getPageSize();
        }
    }

    private final RecordingService service = new RecordingService();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        PageableHandlerMethodArgumentResolver pageable = new PageableHandlerMethodArgumentResolver();
        pageable.setMaxPageSize(100);
        mvc = MockMvcBuilders.standaloneSetup(new LabController(service)).setCustomArgumentResolvers(pageable).build();
    }

    /**
     * Poprawny JSON dociera do usługi i zwraca status utworzenia.
     */
    @Test
    @DisplayName("Poprawny JSON dociera do usługi i zwraca status utworzenia")
    void shouldCallServiceWhenJsonIsValid() throws Exception {
        mvc
            .perform(post("/lab/notes").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"abc\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.text").value("ABC"));
        assertThat(service.calls).containsExactly("abc");
    }

    /**
     * Błędny JSON zatrzymuje przetwarzanie przed kontrolerem.
     */
    @Test
    @DisplayName("Błędny JSON zatrzymuje przetwarzanie przed kontrolerem")
    void shouldRejectRequestBeforeControllerWhenJsonIsMalformed() throws Exception {
        mvc
            .perform(post("/lab/notes").contentType(MediaType.APPLICATION_JSON).content("{\"text\":"))
            .andExpect(status().isBadRequest());
        assertThat(service.calls).isEmpty();
    }

    /**
     * Nagłówek i jego brak dają odpowiednie argumenty.
     */
    @Test
    @DisplayName("Nagłówek i jego brak dają odpowiednie argumenty")
    void shouldBindDefaultWhenHeaderIsMissing() throws Exception {
        mvc.perform(get("/lab/whoami").header("X-Client", "bruno")).andExpect(content().string("client=bruno"));
        mvc.perform(get("/lab/whoami")).andExpect(content().string("client=unknown"));
    }

    /**
     * Domyślny resolver koryguje błędne parametry strony.
     * Dlatego aplikacja ma własny PageQueryArgumentResolver, który zwraca 400.
     */
    @Test
    @DisplayName("Domyślny resolver koryguje błędne parametry strony")
    void shouldClampValuesWhenDefaultPageableResolverReceivesInvalidInput() throws Exception {
        mvc
            .perform(get("/lab/page").param("page", "-1").param("size", "500"))
            .andExpect(status().isOk())
            .andExpect(content().string("0/100"));
    }
}
