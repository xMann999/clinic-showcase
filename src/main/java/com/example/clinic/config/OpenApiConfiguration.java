package com.example.clinic.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies API metadata for springdoc's generated OpenAPI description.
 *
 * <p>Springdoc combines this singleton bean with controller mappings, schemas and operation annotations.
 * The metadata describes the intended public contract; generating a specification does not check runtime behavior.
 * {@code proxyBeanMethods = false} is sufficient because no bean method calls another.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    /**
     * Provides singleton API title, version and contract metadata for springdoc.
     *
     * @return the clinic OpenAPI description
     */
    @Bean
    OpenAPI clinicOpenApi() {
        return new OpenAPI()
            .info(
                new Info()
                    .title("Spring Clinic Showcase API")
                    .version("v1")
                    .description(
                        """
                        Educational API of a small clinic.
                        Time contract: visit dateTime is local clinic time (Europe/Warsaw) WITHOUT offset,
                        e.g. 2030-03-04T10:15:00. birthday is an ISO date.
                        Every error response uses the ApiError shape with a stable `code`.
                        userId in paths is NOT authentication - the showcase has no login module."""
                    )
            );
    }
}
