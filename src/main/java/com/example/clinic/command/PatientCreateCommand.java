package com.example.clinic.command;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Carries patient data and the identifier of an existing owning account.
 *
 * <p>The controller's {@code @Valid} triggers the declared input constraints; constructing this
 * record directly does not validate it. State-dependent checks remain in the service.
 * The document format is three uppercase ASCII letters followed by six digits; it is a format check,
 * not a document authenticity check. Birthday must be in the past. A phone is optional, but when present
 * must contain 9-15 digits with an optional leading plus sign. Dates have no time or zone.
 * The service assigns the owner and checks document uniqueness; no account is created implicitly.
 * The overridden {@code toString} masks the document and omits other personal fields.
 *
 * @param firstName the required first name, at most 100 characters
 * @param lastName the required last name, at most 100 characters
 * @param idCardNo the required document number
 * @param birthday the required birth date
 * @param phoneNumber the optional phone, with null representing absence
 * @param userId the required owning account identifier
 */
public record PatientCreateCommand(
    @NotBlank @Size(max = 100) @Schema(example = "Anna") String firstName,
    @NotBlank @Size(max = 100) @Schema(example = "Nowak") String lastName,
    @NotBlank
    @Pattern(regexp = "[A-Z]{3}[0-9]{6}", message = "must have format ABC123456")
    @Schema(example = "ABC123456")
    String idCardNo,
    @NotNull @Past @Schema(example = "1990-05-17") LocalDate birthday,
    @Pattern(regexp = "\\+?[0-9]{9,15}", message = "must contain 9-15 digits, optionally prefixed with +")
    @Schema(example = "+48600700800")
    String phoneNumber,
    @NotNull @Schema(example = "1") Long userId
) {
    @Override
    public String toString() {
        return "PatientCreateCommand[userId=" + userId + ", idCardNo=***]";
    }
}
