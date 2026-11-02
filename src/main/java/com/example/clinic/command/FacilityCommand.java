package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Carries the full editable facility data for POST and PUT.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * Both fields must be nonblank; this command contains no doctor relationships.
 *
 * @param name the facility name, at most 200 characters
 * @param address the facility address, at most 300 characters
 */
public record FacilityCommand(
    @NotBlank @Size(max = 200) @Schema(example = "Clinic Centre") String name,
    @NotBlank @Size(max = 300) @Schema(example = "ul. Prosta 1, 00-001 Warszawa") String address
) {}
