package com.example.showcaselab.jackson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Mapper JSON jako bean z @Configuration/@Bean, współdzielony przez dwie usługi.
 * <h2>Cel</h2>
 * Wykazać tożsamość i wspólną konfigurację obiektu biblioteki utworzonego przez metodę Bean.
 * <h2>Jak działa przykład</h2>
 * JsonMapper to klasa biblioteki - nie dopiszemy do niej @Component. Metoda @Bean tworzy ją
 * raz (singleton), a kontener wstrzykuje tę samą instancję każdemu odbiorcy.
 * W aplikacji tego NIE robimy: Boot tworzy mapper sam, a my tylko go dostrajamy
 * (JsonMapperBuilderCustomizer) - własny @Bean wyłączyłby część auto-konfiguracji.
 * Kontekst jest izolowany (ApplicationContextRunner) - nie dotyka beanów aplikacji.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Dwie usługi otrzymują ten sam skonfigurowany mapper.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * Używany jest Jackson 3 z pakietów tools.jackson; adnotacje pochodzą z com.fasterxml.jackson.annotation. Konfiguracja lokalnego mappera nie jest konfiguracją Boota.
 */
@DisplayName("Mapper JSON jako bean z @Configuration/@Bean, współdzielony przez dwie usługi.")
class ObjectMapperBeanLabTest {

    @Configuration(proxyBeanMethods = false)
    static class MapperConfig {

        @Bean
        JsonMapper labJsonMapper() {
            return JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        }
    }

    @Component
    record ExportService(JsonMapper mapper) {
        String export(LocalDate date) {
            return mapper.writeValueAsString(date);
        }
    }

    @Component
    record ImportService(JsonMapper mapper) {
        LocalDate read(String json) {
            return mapper.readValue(json, LocalDate.class);
        }
    }

    record Item(String name) {}

    /**
     * Dwie usługi otrzymują ten sam skonfigurowany mapper.
     */
    @Test
    @DisplayName("Dwie usługi otrzymują ten sam skonfigurowany mapper")
    void shouldShareConfiguredMapperWhenTwoServicesAreCreated() {
        new ApplicationContextRunner()
            .withUserConfiguration(MapperConfig.class, ExportService.class, ImportService.class)
            .run(context -> {
                ExportService export = context.getBean(ExportService.class);
                ImportService importer = context.getBean(ImportService.class);

                assertThat(export.mapper()).isSameAs(importer.mapper()).isSameAs(context.getBean(JsonMapper.class));
                assertThat(export.export(LocalDate.of(2030, 1, 8))).isEqualTo("\"2030-01-08\"");
                assertThat(importer.read("\"2030-01-08\"")).isEqualTo(LocalDate.of(2030, 1, 8));
                assertThatThrownBy(() -> export.mapper().readValue("{\"name\":\"a\",\"x\":1}", Item.class)
                ).isInstanceOf(UnrecognizedPropertyException.class);
            });
    }
}
