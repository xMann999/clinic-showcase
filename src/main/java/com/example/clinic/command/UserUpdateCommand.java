package com.example.clinic.command;

import com.example.clinic.domain.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Replaces the editable account email and role through PUT.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * Omitted required fields fail validation rather than retaining their previous values.
 * Passwords use a separate operation; the service normalizes email and checks uniqueness.
 *
 * @param email the required replacement email, at most 255 characters
 * @param role the required replacement role
 */
public record UserUpdateCommand(
    @NotBlank @Email @Size(max = 255) @Schema(example = "anna.nowak@example.test") String email,
    @NotNull @Schema(example = "ADMIN") Role role
) {}
