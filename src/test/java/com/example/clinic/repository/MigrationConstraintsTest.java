package com.example.clinic.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.clinic.error.ConstraintViolationTranslator;
import com.example.clinic.error.ErrorCode;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schemat sprawdzany NIEZALEŻNIE od Hibernate validate (validate nie audytuje indeksów
 * ani wszystkich ograniczeń): metadane bazy, historia Liquibase i zachowanie DB przy
 * wadliwym SQL wykonanym z pominięciem aplikacji.
 */
@DataJpaTest
class MigrationConstraintsTest {

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(dataSource);
    }

    @Test
    void liquibase_appliedAllChangesetsInMasterOrder() {
        List<String> ids = jdbc.queryForList("select id from databasechangelog order by orderexecuted", String.class);

        assertThat(ids).containsExactly(
            "create-patients-table",
            "create-users-table",
            "add-patients-user-fk",
            "create-patients-user-id-index",
            "create-doctors-table",
            "create-facilities-table",
            "create-doctor-facility-table",
            "create-visits-table",
            "add-visit-version",
            "create-visits-doctor-datetime-index"
        );
    }

    @Test
    void indexes_existWithStableNames() {
        List<String> indexes = jdbc.queryForList(
            "select lower(index_name) from information_schema.indexes where table_schema = 'PUBLIC'",
            String.class
        );

        assertThat(indexes).contains(
            "idx_visits_doctor_id_date_time",
            "idx_patients_user_id",
            "idx_visits_patient_id",
            "idx_doctor_facility_facility_id"
        );
    }

    @Test
    void foreignKey_patientWithMissingUser_rejectedByDatabase() {
        assertThatThrownBy(() ->
            jdbc.update(
                "insert into patients (first_name, last_name, id_card_no, birthday, user_id) " +
                "values ('A', 'B', 'FKT000001', DATE '1990-01-01', 999999)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unique_duplicateEmail_rejectedAndTranslatedByConstraintName() {
        jdbc.update("insert into users (email, password_hash, role) values ('dup@example.test', 'h', 'USER')");

        DataIntegrityViolationException exception = org.junit.jupiter.api.Assertions.assertThrows(
            DataIntegrityViolationException.class,
            () -> jdbc.update("insert into users (email, password_hash, role) values ('dup@example.test', 'h', 'USER')")
        );

        assertThat(ConstraintViolationTranslator.translate(exception)).contains(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test
    void notNull_visitWithoutDoctor_rejected() {
        assertThatThrownBy(() ->
            jdbc.update(
                "insert into visits (date_time, duration_in_minutes) values (TIMESTAMP '2030-01-08 10:00:00', 30)"
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void versionColumn_backfilledWithZeroByDefault() {
        jdbc.update("insert into users (id, email, password_hash, role) values (5000, 'v@example.test', 'h', 'USER')");
        jdbc.update(
            "insert into doctors (id, first_name, last_name, specialization, user_id) values (5000, 'A', 'B', 'C', 5000)"
        );
        jdbc.update(
            "insert into visits (date_time, duration_in_minutes, doctor_id) values (TIMESTAMP '2030-01-08 10:00:00', 30, 5000)"
        );

        assertThat(jdbc.queryForObject("select version from visits where doctor_id = 5000", Long.class)).isZero();
    }
}
