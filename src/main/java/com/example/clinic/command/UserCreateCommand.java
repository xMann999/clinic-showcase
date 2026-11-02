package com.example.clinic.command;

import com.example.clinic.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Carries the email, plaintext password and role for account creation.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The service normalizes email and hashes the password. No generated identifier is accepted.
 * The overridden {@code toString} masks the password but includes email; do not treat it as free of personal data.
 * The 8-72 character constraint does not enforce BCrypt's UTF-8 byte limit for non-ASCII passwords.
 *
 * @param email the required valid email, at most 255 characters before normalization
 * @param password the required plaintext password, 8-72 characters
 * @param role the required account role
 */
public record UserCreateCommand(
    @NotBlank @Email @Size(max = 255) @Schema(example = "anna.nowak@example.test") String email,
    @NotBlank @Size(min = 8, max = 72) @Schema(example = "Secret-123", format = "password") String password,
    @NotNull @Schema(example = "USER") Role role
) {
    @Override
    public String toString() {
        return "UserCreateCommand[email=" + email + ", password=***, role=" + role + "]";
    }
}
