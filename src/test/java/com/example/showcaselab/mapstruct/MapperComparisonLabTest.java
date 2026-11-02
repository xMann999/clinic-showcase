package com.example.showcaselab.mapstruct;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.clinic.domain.Patient;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.dto.PatientDto;
import com.example.clinic.mapper.PatientMapper;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * Trzy sposoby mapowania encji na DTO.
 * <h2>Cel</h2>
 * Porównać jawne mapowanie i generator z ograniczonym dopasowaniem po nazwach pól.
 * <h2>Jak działa przykład</h2>
 * - ręczny: pełna kontrola, ale nowe pole w DTO łatwo przeoczyć (kompilator nie ostrzeże,
 *   dopóki konstruktor rekordu ma te same typy na tych samych pozycjach);
 * - MapStruct: kod generowany przy KOMPILACJI (zobacz target/generated-sources/annotations),
 *   brak refleksji w runtime, niezmapowane pole = błąd kompilacji (MapStructCompileLabTest);
 * - refleksja w runtime (tak działają biblioteki typu ModelMapper): mało kodu, ale dopasowanie
 *   po nazwach odbywa się dopiero przy uruchomieniu i po cichu pomija różnice.
 * Mappers.getMapper tworzy implementację bez Springa - wystarcza, bo PatientMapper nie ma
 * współpracowników (uses). Mapper z "uses" wymagałby dostarczenia ich instancji w teście.
 * Zamiast expression = "java(...)" (Java w Stringu, sprawdzana dopiero w wygenerowanym kodzie)
 * aplikacja używa zwykłych metod @Named.
 *
 * <h2>Co pokazują scenariusze</h2>
 * <ul>
 * <li>Mapper ręczny i wygenerowany realizują ten sam kontrakt.</li>
 * <li>Dopasowanie refleksyjne pomija pola pochodne i zagnieżdżone.</li>
 * </ul>
 * <h2>Pułapki i ograniczenia</h2>
 * MapStruct jest uruchamiany rzeczywiście. Refleksyjny wariant jest uproszczonym, celowo niepełnym algorytmem.
 */
@DisplayName("Trzy sposoby mapowania encji na DTO.")
class MapperComparisonLabTest {

    private Patient patient() {
        User owner = new User("owner@example.test", "HASH", Role.USER) {
            @Override
            public Long getId() {
                return 7L;
            }
        };
        Patient patient = new Patient("Anna", "Nowak", "ABC123456", LocalDate.of(1990, 5, 17), "+48600700800") {
            @Override
            public Long getId() {
                return 11L;
            }
        };
        owner.addPatient(patient);
        return patient;
    }

    static PatientDto manual(Patient patient) {
        return new PatientDto(
            patient.getId(),
            patient.getFirstName(),
            patient.getLastName(),
            patient.fullName(),
            patient.getBirthday(),
            patient.getPhoneNumber(),
            patient.getUser().getId()
        );
    }

    /** Uproszczony "ModelMapper": dla każdej składowej rekordu szuka pola encji o tej samej nazwie. */
    static PatientDto reflective(Patient patient) throws Exception {
        RecordComponent[] components = PatientDto.class.getRecordComponents();
        Object[] values = new Object[components.length];
        for (int index = 0; index < components.length; index++) {
            values[index] = readFieldOrNull(patient, components[index].getName());
        }
        Class<?>[] types = Arrays.stream(components).map(RecordComponent::getType).toArray(Class<?>[]::new);
        return PatientDto.class.getDeclaredConstructor(types).newInstance(values);
    }

    private static Object readFieldOrNull(Object source, String name) throws IllegalAccessException {
        for (Field field : Patient.class.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                field.setAccessible(true);
                return field.get(source);
            }
        }
        return null;
    }

    /**
     * Mapper ręczny i wygenerowany realizują ten sam kontrakt.
     */
    @Test
    @DisplayName("Mapper ręczny i wygenerowany realizują ten sam kontrakt")
    void shouldMatchContractWhenUsingGeneratedOrManualMapper() {
        PatientMapper generated = Mappers.getMapper(PatientMapper.class);

        assertThat(generated.toDto(patient())).isEqualTo(manual(patient()));
        assertThat(generated.toDto(patient()).fullName()).isEqualTo("Anna Nowak");
        assertThat(generated.toDto(patient()).userId()).isEqualTo(7L);
        assertThat(generated.getClass().getSimpleName()).isEqualTo("PatientMapperImpl");
    }

    /**
     * Dopasowanie refleksyjne pomija pola pochodne i zagnieżdżone.
     * fullName to metoda, userId to user.id - dopasowanie po nazwach pól ich nie znajduje,
     * a refleksja nie zgłasza żadnego błędu.
     */
    @Test
    @DisplayName("Dopasowanie refleksyjne pomija pola pochodne i zagnieżdżone")
    void shouldMissDerivedFieldsWhenMatchingOnlyFieldNames() throws Exception {
        PatientDto dto = reflective(patient());

        assertThat(dto.lastName()).isEqualTo("Nowak");

        assertThat(dto.fullName()).isNull();
        assertThat(dto.userId()).isNull();
    }
}
