package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Carries descriptive data and existing relationships for a new doctor.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The service loads the account and facilities; this command does not create related resources.
 *
 * <p>Omitted or null {@code facilityIds} becomes an empty set. A supplied set is retained without a defensive
 * copy, so the record is not deeply immutable. Null elements remain available for Bean Validation to report.
 *
 * @param firstName the nonblank first name, at most 100 characters
 * @param lastName the nonblank last name, at most 100 characters
 * @param specialization the nonblank specialization, at most 100 characters
 * @param userId the required account identifier
 * @param facilityIds the facility identifiers, defaulting to an empty set
 */
public record DoctorCreateCommand(
    @NotBlank @Size(max = 100) @Schema(example = "Jan") String firstName,
    @NotBlank @Size(max = 100) @Schema(example = "Kowalski") String lastName,
    @NotBlank @Size(max = 100) @Schema(example = "Cardiology") String specialization,
    @NotNull @Schema(example = "1") Long userId,
    Set<@NotNull Long> facilityIds
) {
    public DoctorCreateCommand {
        facilityIds = facilityIds == null ? Set.of() : facilityIds;
    }
}
