package com.example.clinic.command;

import com.example.clinic.validation.QuarterHour;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Requests a free slot for an existing doctor.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The start is local clinic time without an offset, interpreted in the configured clock zone
 * (default Europe/Warsaw). Validation requires a full quarter-hour and a duration of 15-240 minutes
 * in multiples of 15. Service rules check past times, ambiguous or skipped endpoints, same-day ending
 * and overlap with the doctor's existing slots.
 *
 * @param doctorId the required doctor identifier
 * @param dateTime the required local start without offset
 * @param durationInMinutes the required slot length in minutes
 */
public record VisitCreateCommand(
    @NotNull @Schema(example = "1") Long doctorId,
    @NotNull
    @QuarterHour
    @Schema(example = "2030-03-04T10:15:00", description = "Local clinic time (Europe/Warsaw), no offset")
    LocalDateTime dateTime,
    @NotNull @Min(15) @Max(240) @QuarterHour @Schema(example = "30") Integer durationInMinutes
) {}
