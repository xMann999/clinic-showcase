package com.example.clinic.support;

import com.example.clinic.domain.Doctor;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.domain.Visit;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.FacilityRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fixture'y dla testów integracyjnych. Każda metoda zatwierdza dane we WŁASNEJ transakcji
 * (TransactionTemplate), więc są widoczne dla innych wątków i połączeń - to warunek testów
 * współbieżności. Wartości unikalne (e-mail, dokument) generujemy, aby testy nie zależały
 * od kolejności ani od tego, co utworzył inny test w tej samej bazie.
 */
@TestComponent
public class TestFixtures {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(100_000);

    private final UserRepository users;
    private final PatientRepository patients;
    private final DoctorRepository doctors;
    private final FacilityRepository facilities;
    private final VisitRepository visits;
    private final TransactionTemplate tx;

    public TestFixtures(
        UserRepository users,
        PatientRepository patients,
        DoctorRepository doctors,
        FacilityRepository facilities,
        VisitRepository visits,
        PlatformTransactionManager transactionManager
    ) {
        this.users = users;
        this.patients = patients;
        this.doctors = doctors;
        this.facilities = facilities;
        this.visits = visits;
        this.tx = new TransactionTemplate(transactionManager);
    }

    public static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.test";
    }

    public static String uniqueIdCard() {
        return "TST" + SEQUENCE.incrementAndGet();
    }

    public Long user() {
        return tx.execute(status -> users.save(new User(uniqueEmail(), "{noop}hash", Role.USER)).getId());
    }

    public Long patient(Long userId) {
        return tx.execute(status -> {
            User owner = users.findById(userId).orElseThrow();
            Patient patient = new Patient("Ewa", "Testowa", uniqueIdCard(), LocalDate.of(1990, 1, 1), null);
            owner.addPatient(patient);
            return patients.save(patient).getId();
        });
    }

    public Long facility() {
        return tx.execute(status ->
            facilities.save(new Facility("Facility " + SEQUENCE.incrementAndGet(), "Street 1")).getId()
        );
    }

    public Long doctor() {
        Long userId = user();
        return tx.execute(status ->
            doctors
                .save(
                    new Doctor(
                        "Jan",
                        "Lekarz" + SEQUENCE.incrementAndGet(),
                        "Cardiology",
                        users.findById(userId).orElseThrow()
                    )
                )
                .getId()
        );
    }

    public Long visit(Long doctorId, LocalDateTime start, int minutes) {
        return tx.execute(status ->
            visits.save(new Visit(doctors.findById(doctorId).orElseThrow(), start, minutes)).getId()
        );
    }

    public TransactionTemplate transactionTemplate() {
        return tx;
    }
}
