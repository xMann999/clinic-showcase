package com.example.showcaselab.di;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Scope;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.RequestScope;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.SessionScope;

/**
 * Zakresy beanów - porównujemy TOŻSAMOŚĆ instancji, nie tylko definicje.
 * <h2>Cel</h2>
 * Powiązać tożsamość instancji z kontenerem, prototypem, żądaniem i sesją.
 * <h2>Jak działa przykład</h2>
 * Pułapka: singleton z prototypem wstrzykniętym w konstruktorze dostaje JEDNĄ instancję na zawsze.
 * Nową instancję przy każdym użyciu daje ObjectProvider.getObject().
 * Singleton Springa = jedna instancja na KONTENER, nie na JVM (inaczej niż klasyczny wzorzec Singleton).
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Singleton zachowuje tożsamość, prototyp tworzy nową instancję.</li>
 * <li>Provider pobiera nowy prototyp przy każdym użyciu.</li>
 * <li>Dwa kontenery mają różne singletony.</li>
 * <li>Zakres żądania współdzieli obiekt tylko w jednym żądaniu.</li>
 * <li>Zakres sesji współdzieli obiekt między jej żądaniami.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Małe konteksty nie skanują aplikacji. MiniContainer demonstruje tylko rekurencyjne składanie grafu, bez pełnego cyklu życia Springa.
 */
@DisplayName("Zakresy beanów - porównujemy TOŻSAMOŚĆ instancji, nie tylko definicje.")
class BeanScopesLabTest {

    /** Klasa bazowa nie jest beanem; gdyby była, wszystkie podklasy pasowałyby do getBean(Ticket.class). */
    abstract static class Ticket {}

    static class SingletonTicket extends Ticket {}

    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    static class PrototypeTicket extends Ticket {}

    @Scope(WebApplicationContext.SCOPE_REQUEST)
    static class RequestTicket extends Ticket {}

    @Scope(WebApplicationContext.SCOPE_SESSION)
    static class SessionTicket extends Ticket {}

    static class SingletonHolder {

        final PrototypeTicket injectedOnce;
        final ObjectProvider<PrototypeTicket> provider;

        SingletonHolder(PrototypeTicket injectedOnce, ObjectProvider<PrototypeTicket> provider) {
            this.injectedOnce = injectedOnce;
            this.provider = provider;
        }
    }

    private AnnotationConfigApplicationContext context() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerScope(WebApplicationContext.SCOPE_REQUEST, new RequestScope());
        context.getBeanFactory().registerScope(WebApplicationContext.SCOPE_SESSION, new SessionScope());
        context.register(
            SingletonTicket.class,
            PrototypeTicket.class,
            RequestTicket.class,
            SessionTicket.class,
            SingletonHolder.class
        );
        context.refresh();
        return context;
    }

    private RequestAttributes previousRequest;

    @BeforeEach
    void captureRequest() {
        previousRequest = RequestContextHolder.getRequestAttributes();
    }

    @AfterEach
    void resetRequest() {
        completeRequest();
        RequestContextHolder.setRequestAttributes(previousRequest);
    }

    /**
     * Singleton zachowuje tożsamość, prototyp tworzy nową instancję.
     */
    @Test
    @DisplayName("Singleton zachowuje tożsamość, prototyp tworzy nową instancję")
    void shouldReuseOnlySingletonWhenBeansAreRequestedTwice() {
        try (var context = context()) {
            assertThat(context.getBean(SingletonTicket.class)).isSameAs(context.getBean(SingletonTicket.class));
            assertThat(context.getBean(PrototypeTicket.class)).isNotSameAs(context.getBean(PrototypeTicket.class));
        }
    }

    /**
     * Provider pobiera nowy prototyp przy każdym użyciu.
     */
    @Test
    @DisplayName("Provider pobiera nowy prototyp przy każdym użyciu")
    void shouldCreatePrototypeWhenProviderIsCalled() {
        try (var context = context()) {
            SingletonHolder holder = context.getBean(SingletonHolder.class);

            assertThat(context.getBean(SingletonHolder.class).injectedOnce).isSameAs(holder.injectedOnce);
            assertThat(holder.provider.getObject()).isNotSameAs(holder.provider.getObject());
        }
    }

    /**
     * Dwa kontenery mają różne singletony.
     */
    @Test
    @DisplayName("Dwa kontenery mają różne singletony")
    void shouldUseDifferentSingletonsWhenContainersDiffer() {
        try (var first = context(); var second = context()) {
            assertThat(first.getBean(SingletonTicket.class)).isNotSameAs(second.getBean(SingletonTicket.class));
        }
    }

    /**
     * Zakres żądania współdzieli obiekt tylko w jednym żądaniu.
     */
    @Test
    @DisplayName("Zakres żądania współdzieli obiekt tylko w jednym żądaniu")
    void shouldReuseBeanWhenRequestIsUnchanged() {
        try (var context = context()) {
            bindRequest(new MockHttpServletRequest());
            RequestTicket a1 = context.getBean(RequestTicket.class);
            RequestTicket a2 = context.getBean(RequestTicket.class);
            bindRequest(new MockHttpServletRequest());
            RequestTicket b = context.getBean(RequestTicket.class);

            assertThat(a1).isSameAs(a2);
            assertThat(b).isNotSameAs(a1);
        }
    }

    /**
     * Zakres sesji współdzieli obiekt między jej żądaniami.
     */
    @Test
    @DisplayName("Zakres sesji współdzieli obiekt między jej żądaniami")
    void shouldReuseBeanWhenSessionIsUnchanged() {
        try (var context = context()) {
            MockHttpSession session = new MockHttpSession();
            bindRequest(requestWith(session));
            SessionTicket first = context.getBean(SessionTicket.class);
            bindRequest(requestWith(session));
            SessionTicket sameSession = context.getBean(SessionTicket.class);
            bindRequest(requestWith(new MockHttpSession()));
            SessionTicket otherSession = context.getBean(SessionTicket.class);

            assertThat(sameSession).isSameAs(first);
            assertThat(otherSession).isNotSameAs(first);
        }
    }

    private static MockHttpServletRequest requestWith(MockHttpSession session) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        return request;
    }

    private static void completeRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.requestCompleted();
        }
    }

    private static void bindRequest(MockHttpServletRequest request) {
        completeRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
}
