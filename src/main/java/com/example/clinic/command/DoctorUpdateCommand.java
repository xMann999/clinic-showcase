package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Replaces a doctor's descriptive fields through PUT.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The account and facilities are outside this replacement contract.
 *
 * @param firstName the required first name, at most 100 characters
 * @param lastName the required last name, at most 100 characters
 * @param specialization the required specialization, at most 100 characters
 */
public record DoctorUpdateCommand(
    @NotBlank @Size(max = 100) @Schema(example = "Jan") String firstName,
    @NotBlank @Size(max = 100) @Schema(example = "Kowalski") String lastName,
    @NotBlank @Size(max = 100) @Schema(example = "Neurology") String specialization
) {}
