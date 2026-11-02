package com.example.clinic.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.logging.TimingAspect;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import com.example.clinic.service.UserService;
import com.example.clinic.support.FixedClockConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Profil "demo": seeder danych i aspekt pomiaru czasu są aktywne; seeder jest idempotentny.
 */
@SpringBootTest
@ActiveProfiles({ "test", "demo" })
@ExtendWith(OutputCaptureExtension.class)
@Import(FixedClockConfiguration.class)
class DemoProfileTest {

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private UserRepository users;

    @Autowired
    private VisitRepository visits;

    @Autowired
    private UserService userService;

    @Autowired
    private TimingAspect timingAspect;

    @Test
    void seeder_secondRunAddsNothing() {
        long usersBefore = users.count();
        long visitsBefore = visits.count();
        assertThat(users.existsByEmail(DemoDataSeeder.DEMO_EMAIL)).isTrue();

        seeder.run(null);

        assertThat(users.count()).isEqualTo(usersBefore);
        assertThat(visits.count()).isEqualTo(visitsBefore).isEqualTo(4);
    }

    @Test
    void timingAspect_measuresServiceCalls(CapturedOutput output) {
        assertThat(timingAspect).isNotNull();
        userService.list(org.springframework.data.domain.PageRequest.of(0, 5));

        assertThat(output.getAll()).contains("UserService.list(..) took");
    }
}
