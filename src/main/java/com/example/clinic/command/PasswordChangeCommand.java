package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the single required password for the account password PATCH.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The overridden {@code toString} masks the password; the accessor still exposes its original value.
 * The 8-72 character constraint does not enforce BCrypt's UTF-8 byte limit for non-ASCII passwords.
 *
 * @param password the required plaintext password to encode
 */
public record PasswordChangeCommand(
    @NotBlank @Size(min = 8, max = 72) @Schema(format = "password", example = "New-Secret-456") String password
) {
    @Override
    public String toString() {
        return "PasswordChangeCommand[password=***]";
    }
}
