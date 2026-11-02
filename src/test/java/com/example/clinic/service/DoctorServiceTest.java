package com.example.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.example.clinic.command.DoctorCreateCommand;
import com.example.clinic.domain.Facility;
import com.example.clinic.domain.User;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.DoctorMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.FacilityRepository;
import com.example.clinic.repository.UserRepository;
import com.example.clinic.repository.VisitRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Protects creation error precedence and the absence of writes when related resources are missing. */
@ExtendWith(MockitoExtension.class)
class DoctorServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorService service;

    @Test
    void missingFacilities_reportFirstMissingRequestedIdWithoutSaving() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        Facility present = mock(Facility.class);
        when(present.getId()).thenReturn(20L);
        var ids = new LinkedHashSet<>(List.of(30L, 20L, 10L));
        when(facilityRepository.findAllById(ids)).thenReturn(List.of(present));

        assertThatThrownBy(() -> service.create(command(ids)))
            .isInstanceOfSatisfying(DomainException.class, failure ->
                assertThat(failure.code()).isEqualTo(ErrorCode.FACILITY_NOT_FOUND)
            )
            .hasMessageContaining("30");
        verify(doctorRepository, never()).save(any());
        verifyNoInteractions(doctorMapper);
        verify(facilityRepository).findAllById(ids);
    }

    @Test
    void reusedAccount_isRejectedBeforeFacilitiesAreLoaded() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(doctorRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.create(command(new LinkedHashSet<>(List.of(30L)))))
            .isInstanceOfSatisfying(DomainException.class, failure ->
                assertThat(failure.code()).isEqualTo(ErrorCode.USER_ALREADY_DOCTOR)
            )
            .hasMessageContaining("already a doctor");
        verifyNoInteractions(facilityRepository, doctorMapper);
        verify(doctorRepository, never()).save(any());
    }

    private static DoctorCreateCommand command(LinkedHashSet<Long> ids) {
        return new DoctorCreateCommand("Jan", "Nowak", "Cardiology", 1L, ids);
    }
}
