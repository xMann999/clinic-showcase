package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Identifies the patient to book into the visit named in the request path.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The facade checks that the patient exists and belongs to the supplied account; the identifier is not authentication.
 *
 * @param patientId the required identifier of an existing patient
 */
public record BookingCommand(@NotNull @Schema(example = "1") Long patientId) {}
