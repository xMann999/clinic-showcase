package com.example.clinic.service;

import com.example.clinic.command.PasswordChangeCommand;
import com.example.clinic.command.UserCreateCommand;
import com.example.clinic.command.UserUpdateCommand;
import com.example.clinic.domain.EmailAddress;
import com.example.clinic.domain.User;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.UserDto;
import com.example.clinic.error.DomainException;
import com.example.clinic.error.ErrorCode;
import com.example.clinic.mapper.UserMapper;
import com.example.clinic.repository.DoctorRepository;
import com.example.clinic.repository.PatientRepository;
import com.example.clinic.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages accounts and password hashing.
 *
 * <h2>Responsibility</h2>
 * <p>Coordinates injected account, patient and doctor repositories, the encoder and DTO mapper.
 *
 * <h2>How it works</h2>
 * <p>Normalizes emails before checking or saving them, hashes plaintext passwords and prevents deletion
 * of accounts that retain patients or a doctor profile.
 *
 * <h2>Design choices</h2>
 * <p>No current-request data is stored in service fields. DTO mapping excludes password hashes;
 * normalization and unique constraints complement the non-atomic email pre-check.
 *
 * <h2>Important details</h2>
 * <p>Calls through Spring's transactional proxy start or join a REQUIRED transaction. Calls within
 * this instance do not pass through that proxy. Updates to entities loaded in a write transaction are
 * tracked by the persistence context and synchronized at flush; flush is not commit. Default rollback
 * rules apply to unchecked failures. Read-only operations are hints, not a database permission boundary.
 * Private entity lookups run inside the public operation's transaction, including subsequent updates
 * and DTO mapping. Public operations expose DTOs rather than entities. HTTP validation and sort-property
 * allowlists belong to the controller boundary.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Creates an account with normalized email and an encoded password.
     *
     * <p>The email pre-check improves the common error path; concurrent inserts still rely on the database
     * unique constraint and its translation by the exception advice.
     *
     * @param command the validated email, plaintext password and role
     * @return the created account without the password hash
     * @throws DomainException if the normalized email already exists
     */
    @Transactional
    public UserDto create(UserCreateCommand command) {
        String email = EmailAddress.normalize(command.email());
        if (userRepository.existsByEmail(email)) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_EXISTS, "An account with this e-mail already exists");
        }
        User user = userRepository.save(new User(email, passwordEncoder.encode(command.password()), command.role()));
        log.info("Created user {}", user.getId());
        return userMapper.toDto(user);
    }

    /**
     * Loads one account using a read-only transaction hint.
     *
     * <p>Read-only is not a universal write prohibition or an authorization check.
     *
     * @param id the account identifier
     * @return the account representation without its password hash
     * @throws DomainException if the account does not exist
     */
    @Transactional(readOnly = true)
    public UserDto get(Long id) {
        return userMapper.toDto(requireUser(id));
    }

    /**
     * Maps a page of accounts while retaining the source page metadata.
     *
     * @param pageable the requested paging and allowed sorting
     * @return account DTOs with page number, size and totals
     */
    @Transactional(readOnly = true)
    public PageResponse<UserDto> list(Pageable pageable) {
        return PageResponse.from(userRepository.findAll(pageable).map(userMapper::toDto));
    }

    /**
     * Replaces normalized email and role on the managed account.
     *
     * <p>Dirty checking synchronizes changes without an additional repository save. A changed email is
     * checked for reuse; the database remains responsible for atomic uniqueness.
     *
     * @param id the account identifier
     * @param command the replacement email and role
     * @return the updated account representation
     * @throws DomainException if the account is missing or the email is already used
     */
    @Transactional
    public UserDto update(Long id, UserUpdateCommand command) {
        User user = requireUser(id);
        String email = EmailAddress.normalize(command.email());
        if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
            throw new DomainException(ErrorCode.EMAIL_ALREADY_EXISTS, "An account with this e-mail already exists");
        }
        user.changeEmail(email);
        user.changeRole(command.role());
        return userMapper.toDto(user);
    }

    /**
     * Hashes the new plaintext password and replaces the managed account's stored hash.
     *
     * <p>Only the account identifier is logged. No plaintext password is returned.
     *
     * @param id the account identifier
     * @param command the validated password request
     * @throws DomainException if the account does not exist
     */
    @Transactional
    public void changePassword(Long id, PasswordChangeCommand command) {
        requireUser(id).changePasswordHash(passwordEncoder.encode(command.password()));
        log.info("Password changed for user {}", id);
    }

    /**
     * Deletes an account only when it has no patients or doctor profile.
     *
     * <p>The pre-check supplies a domain error; database references remain the final concurrency guard.
     *
     * @param id the account identifier
     * @throws DomainException if the account is missing or still referenced
     */
    @Transactional
    public void delete(Long id) {
        User user = requireUser(id);
        if (patientRepository.existsByUserId(id) || doctorRepository.existsByUserId(id)) {
            throw new DomainException(
                ErrorCode.RESOURCE_IN_USE,
                "User " + id + " still has patients or a doctor profile"
            );
        }
        userRepository.delete(user);
    }

    /**
     * Loads an account entity inside the calling operation's existing transaction.
     *
     * <p>This local lookup does not cross a proxy or open a transaction. The public operation keeps the
     * persistence context open for subsequent mutations and mapping.
     *
     * @param id the account identifier
     * @return the account, managed while its persistence context remains open
     * @throws DomainException if the account does not exist
     */
    private User requireUser(Long id) {
        return userRepository
            .findById(id)
            .orElseThrow(() -> DomainException.notFound(ErrorCode.USER_NOT_FOUND, "User", id));
    }
}
