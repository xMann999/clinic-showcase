package com.example.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.clinic.mapper.PatientMapper;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/** Protects filter precedence and normalization at the public patient search boundary. */
@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private PatientMapper patientMapper;

    @InjectMocks
    private PatientService service;

    @Test
    void emailTakesPrecedenceOverSurnameAndIsNormalized() {
        var pageable = PageRequest.of(2, 5);
        when(patientRepository.findAllByUserEmail("a@example.com", pageable)).thenReturn(Page.empty(pageable));

        var result = service.search(" A@EXAMPLE.COM ", "ignored", pageable);

        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.content()).isEmpty();
        verify(patientRepository).findAllByUserEmail("a@example.com", pageable);
        verifyNoMoreInteractions(patientRepository);
    }

    @Test
    void blankEmailUsesStrippedSurname() {
        var pageable = PageRequest.of(0, 10);
        when(patientRepository.findAllByLastNameContainingIgnoreCase("Nowak", pageable)).thenReturn(
            Page.empty(pageable)
        );

        service.search(" ", " Nowak ", pageable);

        verify(patientRepository).findAllByLastNameContainingIgnoreCase("Nowak", pageable);
        verifyNoMoreInteractions(patientRepository);
    }

    @Test
    void absentFiltersListAllPatients() {
        var pageable = PageRequest.of(0, 10);
        when(patientRepository.findAll(pageable)).thenReturn(Page.empty(pageable));

        service.search(null, " ", pageable);

        verify(patientRepository).findAll(pageable);
        verifyNoMoreInteractions(patientRepository);
    }
}
