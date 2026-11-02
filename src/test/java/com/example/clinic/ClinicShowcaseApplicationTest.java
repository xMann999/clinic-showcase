package com.example.clinic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.clinic.logging.TimingAspect;
import com.example.clinic.service.UserService;
import com.example.clinic.support.FixedClockConfiguration;
import java.time.Clock;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Smoke test pełnego kontekstu: Liquibase + Hibernate validate + MVC + springdoc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FixedClockConfiguration.class)
class ClinicShowcaseApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads_withConfiguredBeansAndWithoutDemoAspect() {
        Clock systemClock = context.getBean("clinicClock", Clock.class);

        assertThat(systemClock.getZone()).isEqualTo(ZoneId.of("Europe/Warsaw"));
        assertThat(context.getBean(Clock.class).instant()).isEqualTo(FixedClockConfiguration.fixedClock().instant());
        assertThat(context.getBeanNamesForType(TimingAspect.class)).isEmpty();
        // @Transactional sprawia, że do kontrolera trafia proxy serwisu, a nie sama klasa.
        assertThat(AopUtils.isAopProxy(context.getBean(UserService.class))).isTrue();
    }

    @Test
    void openApi_describesEndpointsAndErrorContract() throws Exception {
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paths['/api/patients'].post").exists())
            .andExpect(
                jsonPath("$.paths['/api/users/{userId}/visits/{visitId}/booking'].post.responses['409']").exists()
            )
            .andExpect(jsonPath("$.components.schemas.ApiError.properties.code").exists())
            .andExpect(jsonPath("$.components.schemas.FieldViolation").exists())
            .andExpect(
                content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("passwordHash")))
            );
    }

    @Test
    void swaggerUi_isServed() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
