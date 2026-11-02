package com.example.clinic.config;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.FacilityRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds synthetic clinic data when the demo profile is active.
 *
 * <h2>Responsibility</h2>
 * <p>Creates sample accounts, patients, facilities, a doctor and tomorrow's visit slots at startup.
 *
 * <h2>How it works</h2>
 * <p>The runner checks the demo email and skips the batch when that account already exists.
 * Its transactional method explicitly saves patients because the user relationship has no persist cascade.
 *
 * <h2>Design choices</h2>
 * <p>Demo content is separate from schema migrations and enabled by the {@code demo} profile.
 *
 * <h2>Important details</h2>
 * <p>The email pre-check is not a concurrency lock or a repair of partially existing demo data.
 * This runner creates slots directly rather than using the service's locking and time-validation protocol.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    static final String DEMO_EMAIL = "demo.user@example.test";

    private final UserRepository userRepository;
    private final com.example.clinic.repository.PatientRepository patientRepository;
    private final FacilityRepository facilityRepository;
    private final DoctorRepository doctorRepository;
    private final VisitRepository visitRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    /**
     * Seeds the demo graph in one startup write transaction unless the demo account already exists.
     *
     * <p>Each patient is saved explicitly because account ownership has no persist cascade. This direct
     * entity/repository path bypasses VisitService time validation and slot-creation locking.
     *
     * @param args the startup arguments, unused
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEMO_EMAIL)) {
            log.info("Demo data already present - skipping seeding");
            return;
        }
        User account = userRepository.save(new User(DEMO_EMAIL, passwordEncoder.encode("Demo-Password-1"), Role.USER));

        for (Patient patient : java.util.List.of(
            new Patient("Ada", "Testowa", "AAA000001", LocalDate.of(1990, 1, 15), "+48500000001"),
            new Patient("Olek", "Testowy", "AAA000002", LocalDate.of(2015, 6, 1), null)
        )) {
            account.addPatient(patient);
            patientRepository.save(patient);
        }

        User doctorAccount = userRepository.save(
            new User("demo.doctor@example.test", passwordEncoder.encode("Demo-Password-2"), Role.USER)
        );
        Facility centre = facilityRepository.save(new Facility("Demo Clinic Centre", "ul. Przykładowa 1, Warszawa"));
        Facility east = facilityRepository.save(new Facility("Demo Clinic East", "ul. Testowa 2, Warszawa"));
        Doctor doctor = new Doctor("Jan", "Demonstracyjny", "Cardiology", doctorAccount);
        doctor.addFacility(centre);
        doctor.addFacility(east);
        doctorRepository.save(doctor);

        LocalDate tomorrow = LocalDate.now(clock).plusDays(1);
        for (int slot = 0; slot < 4; slot++) {
            visitRepository.save(new Visit(doctor, tomorrow.atTime(LocalTime.of(9, 0)).plusMinutes(30L * slot), 30));
        }
        log.info("Demo data seeded: user {}, doctor {}", account.getId(), doctor.getId());
    }
}
