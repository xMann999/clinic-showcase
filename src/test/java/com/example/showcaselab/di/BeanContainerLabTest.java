package com.example.showcaselab.di;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.showcaselab.di.scan.ScannedRepository;
import com.example.showcaselab.di.scan.ScannedService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Skanowanie komponentów, wybór jednego z wielu beanów i błędy kontenera.
 * <h2>Cel</h2>
 * Rozróżnić rejestrację komponentów, wybór kandydata i opcjonalność zależności na małych kontekstach.
 * <h2>Jak działa przykład</h2>
 * ReminderSender to także przykład wzorca Strategy: wspólny interfejs, wymienne implementacje,
 * wybór przez konfigurację (@Primary/@Qualifier) zamiast if-ów w kodzie wywołującym.
 * Celowo błędne konfiguracje uruchamiamy w izolowanych kontekstach i sprawdzamy asercją
 * przyczynę porażki startu - główny kontekst aplikacji pozostaje nietknięty.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Skanowanie rejestruje komponenty tylko ze wskazanego pakietu.</li>
 * <li>Dwie równorzędne implementacje uniemożliwiają wybór.</li>
 * <li>Kwalifikator wybiera innego beana niż Primary.</li>
 * <li>Zależność opcjonalna dopuszcza brak beana.</li>
 * <li>Cykl konstruktorów zatrzymuje tworzenie beanów.</li>
 * <li>Wydzielenie wspólnej odpowiedzialności usuwa cykl.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Małe konteksty nie skanują aplikacji. MiniContainer demonstruje tylko rekurencyjne składanie grafu, bez pełnego cyklu życia Springa.
 */
@DisplayName("Skanowanie komponentów, wybór jednego z wielu beanów i błędy kontenera.")
class BeanContainerLabTest {

    interface ReminderSender {
        String send(String text);
    }

    static class SmsReminderSender implements ReminderSender {

        public String send(String text) {
            return "SMS: " + text;
        }
    }

    static class EmailReminderSender implements ReminderSender {

        public String send(String text) {
            return "EMAIL: " + text;
        }
    }

    record ReminderClient(ReminderSender sender) {}

    record QualifiedClient(@Qualifier("emailSender") ReminderSender sender) {}

    record AllSendersClient(List<ReminderSender> senders) {}

    record OptionalClient(Optional<ReminderSender> sender, ObjectProvider<ReminderSender> provider) {}

    @Configuration(proxyBeanMethods = false)
    static class TwoSenders {

        @Bean
        ReminderSender smsSender() {
            return new SmsReminderSender();
        }

        @Bean
        ReminderSender emailSender() {
            return new EmailReminderSender();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoSendersWithPrimary {

        @Bean
        @Primary
        ReminderSender smsSender() {
            return new SmsReminderSender();
        }

        @Bean
        ReminderSender emailSender() {
            return new EmailReminderSender();
        }
    }

    /**
     * Skanowanie rejestruje komponenty tylko ze wskazanego pakietu.
     * Klasa spoza skanowanego pakietu nie jest beanem - kontener o niej nie wie.
     */
    @Test
    @DisplayName("Skanowanie rejestruje komponenty tylko ze wskazanego pakietu")
    void shouldFindComponentsWhenScanningTheirPackage() {
        try (var context = new AnnotationConfigApplicationContext("com.example.showcaselab.di.scan")) {
            assertThat(context.getBeanDefinitionNames()).contains(
                "scannedService",
                "scannedRepository",
                "scannedController"
            );
            assertThat(context.getBean(ScannedService.class).repository()).isSameAs(
                context.getBean(ScannedRepository.class)
            );

            assertThatThrownBy(() -> context.getBean(SmsReminderSender.class)).isInstanceOf(
                NoSuchBeanDefinitionException.class
            );
        }
    }

    /**
     * Dwie równorzędne implementacje uniemożliwiają wybór.
     */
    @Test
    @DisplayName("Dwie równorzędne implementacje uniemożliwiają wybór")
    void shouldRejectAmbiguityWhenTwoCandidatesExist() {
        new ApplicationContextRunner()
            .withUserConfiguration(TwoSenders.class, ReminderClient.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().isInstanceOf(NoUniqueBeanDefinitionException.class);
            });
    }

    /**
     * Kwalifikator wybiera innego beana niż Primary.
     */
    @Test
    @DisplayName("Kwalifikator wybiera innego beana niż Primary")
    void shouldOverridePrimaryWhenQualifierIsPresent() {
        new ApplicationContextRunner()
            .withUserConfiguration(
                TwoSendersWithPrimary.class,
                ReminderClient.class,
                QualifiedClient.class,
                AllSendersClient.class
            )
            .run(context -> {
                assertThat(context.getBean(ReminderClient.class).sender().send("x")).isEqualTo("SMS: x");
                assertThat(context.getBean(QualifiedClient.class).sender()).isSameAs(
                    context.getBean("emailSender", ReminderSender.class)
                );
                assertThat(context.getBean(AllSendersClient.class).senders()).containsExactlyInAnyOrder(
                    context.getBean("smsSender", ReminderSender.class),
                    context.getBean("emailSender", ReminderSender.class)
                );
            });
    }

    /**
     * Zależność opcjonalna dopuszcza brak beana.
     */
    @Test
    @DisplayName("Zależność opcjonalna dopuszcza brak beana")
    void shouldAllowMissingBeanWhenDependencyIsOptional() {
        new ApplicationContextRunner()
            .withUserConfiguration(OptionalClient.class)
            .run(context -> {
                OptionalClient client = context.getBean(OptionalClient.class);
                assertThat(client.sender()).isEmpty();
                assertThat(client.provider().getIfAvailable()).isNull();
            });
    }

    record CycleA(CycleB b) {}

    /** Bez implementacji wymagany argument konstruktora zatrzymuje start, w przeciwieństwie do Optional. */
    @Test
    @DisplayName("Brak beana uniemożliwia wstrzyknięcie wymaganej zależności")
    void shouldRejectStartupWhenRequiredDependencyIsMissing() {
        new ApplicationContextRunner()
            .withUserConfiguration(ReminderClient.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).rootCause().isInstanceOf(NoSuchBeanDefinitionException.class);
            });
    }

    record CycleB(CycleA a) {}

    /**
     * Cykl konstruktorów zatrzymuje tworzenie beanów.
     */
    @Test
    @DisplayName("Cykl konstruktorów zatrzymuje tworzenie beanów")
    void shouldRejectCycleWhenConstructorsDependOnEachOther() {
        new ApplicationContextRunner()
            .withUserConfiguration(CycleA.class, CycleB.class)
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure())
                    .rootCause()
                    .isInstanceOf(BeanCurrentlyInCreationException.class);
            });
    }

    /** Rozwiązanie cyklu: wspólną odpowiedzialność wydzielamy do trzeciego beana. */
    record SharedRules() {}

    record FixedA(SharedRules rules) {}

    record FixedB(SharedRules rules) {}

    /**
     * Wydzielenie wspólnej odpowiedzialności usuwa cykl.
     */
    @Test
    @DisplayName("Wydzielenie wspólnej odpowiedzialności usuwa cykl")
    void shouldShareRulesWhenCycleIsRemoved() {
        new ApplicationContextRunner()
            .withUserConfiguration(SharedRules.class, FixedA.class, FixedB.class)
            .run(context ->
                assertThat(context.getBean(FixedA.class).rules()).isSameAs(context.getBean(FixedB.class).rules())
            );
    }
}
