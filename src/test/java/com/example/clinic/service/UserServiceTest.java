package com.example.clinic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.clinic.command.UserCreateCommand;
import com.example.clinic.command.UserUpdateCommand;
import com.example.clinic.domain.Role;
import com.example.clinic.domain.User;
import com.example.clinic.dto.UserDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.UserMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test jednostkowy serwisu: bez Springa i bez bazy.
 *
 * - @Mock: dubler repozytorium - stubujemy odpowiedzi (when...thenReturn) i sprawdzamy
 *   interakcje (verify). Stubowanie przygotowuje dane, weryfikacja sprawdza skutek uboczny.
 * - @Spy z prawdziwym mapperem (Mappers.getMapper): mapowanie nie jest udawane, więc błąd
 *   w mapperze nie schowa się za dowolnym stubem.
 * - @InjectMocks: Mockito tworzy UserService przez konstruktor z powyższymi dublerami.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @InjectMocks
    private UserService userService;

    @Test
    void create_newEmail_savesNormalizedEmailAndHashedPassword() {
        // given
        when(userRepository.existsByEmail("anna@example.test")).thenReturn(false);
        when(passwordEncoder.encode("Secret-123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        UserDto result = userService.create(new UserCreateCommand("  Anna@Example.TEST ", "Secret-123", Role.USER));

        // then
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("anna@example.test");
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("HASHED").isNotEqualTo("Secret-123");
        assertThat(result.email()).isEqualTo("anna@example.test");
    }

    @Test
    void create_existingEmail_throwsConflictAndDoesNotSave() {
        when(userRepository.existsByEmail("anna@example.test")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(new UserCreateCommand("anna@example.test", "Secret-123", Role.USER))
        ).isInstanceOfSatisfying(DomainException.class, exception ->
            assertThat(exception.code()).isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS)
        );
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_managedEntity_changesFieldsWithoutCallingSave() {
        User user = new User("old@example.test", "HASH", Role.USER);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail("new@example.test")).thenReturn(false);

        userService.update(5L, new UserUpdateCommand("NEW@example.test", Role.ADMIN));

        assertThat(user.getEmail()).isEqualTo("new@example.test");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        // Zapis wykona dirty checking przy commit - serwis nie woła save().
        verify(userRepository, never()).save(any());
    }

    @Test
    void get_missingUser_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.get(99L)).isInstanceOfSatisfying(DomainException.class, exception ->
            assertThat(exception.code()).isEqualTo(ErrorCode.USER_NOT_FOUND)
        );
    }

    @Test
    void delete_userWithPatients_throwsResourceInUseAndKeepsUser() {
        User user = new User("a@example.test", "HASH", Role.USER);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(patientRepository.existsByUserId(1L)).thenReturn(true);

        assertThatThrownBy(() -> userService.delete(1L)).isInstanceOfSatisfying(DomainException.class, exception ->
            assertThat(exception.code()).isEqualTo(ErrorCode.RESOURCE_IN_USE)
        );
        verify(userRepository, never()).delete(any());
    }
}
