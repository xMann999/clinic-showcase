package com.example.clinic.controller;

import com.example.clinic.command.PatientCreateCommand;
import com.example.clinic.command.PatientUpdateCommand;
import com.example.clinic.controller.paging.PageQuery;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.PatientDto;
import com.example.clinic.error.ApiError;
import com.example.clinic.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Exposes HTTP operations for patients.
 *
 * <h2>Responsibility</h2>
 * <p>Owns request binding and response status/header choices; delegates business work to the injected
 * service. Lombok supplies constructor injection for final collaborators.
 *
 * <h2>How it works</h2>
 * <p>Spring MVC binds path identifiers, optional query parameters and JSON command bodies.
 * {@code @Valid} checks body constraints before delegation. Returned DTOs are serialized directly;
 * {@code @ResponseStatus} selects non-default success statuses and creation writes Location to the servlet response.
 *
 * <h2>Design choices</h2>
 * <p>DTOs define the public boundary rather than exposing persistence entities. Paging operations use
 * {@code PageQuery} to validate syntax and resource-specific sort properties.
 *
 * <h2>Important details</h2>
 * <p>Patient DTOs exclude identity documents and expose only the owning account identifier.
 * The exception advice translates validation and business failures into HTTP errors; this controller
 * does not catch them. Writes are transactional in the delegated application layer.
 */
@Tag(name = "Patients", description = "Patients belonging to user accounts")
@RestController
@RequestMapping("/api/patients")
@RequiredArgsConstructor
public class PatientController {

    /** HTTP sort properties accepted for this resource; services receive the validated pageable. */
    private static final Set<String> SORTABLE = Set.of("id", "lastName", "firstName", "birthday");

    private final PatientService patientService;

    /**
     * Creates a patient for an existing account and returns 201 with the resource URI in Location.
     *
     * @param command the input values for this operation
     * @param response the servlet response receiving the Location header
     * @return the created resource representation
     */
    @Operation(summary = "Create a patient for an existing user account")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "201", description = "Created; Location points to the new patient"),
            @ApiResponse(
                responseCode = "400",
                description = "Validation failed / malformed JSON",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "ID_CARD_ALREADY_EXISTS",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PatientDto create(@Valid @RequestBody PatientCreateCommand command, HttpServletResponse response) {
        PatientDto created = patientService.create(command);
        String location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUriString();
        response.setHeader(HttpHeaders.LOCATION, location);
        return created;
    }

    /**
     * Returns a patient DTO with 200, or propagates a missing-resource error.
     *
     * @param id the resource identifier
     * @return the requested resource representation
     */
    @Operation(summary = "Get a patient")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(
                responseCode = "404",
                description = "PATIENT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping("/{id}")
    public PatientDto get(@PathVariable Long id) {
        return patientService.get(id);
    }

    /**
     * Returns a 200 page filtered by optional email or surname query parameters.
     *
     * <p>A nonblank email takes precedence and is normalized for an exact account-email match.
     * Otherwise a nonblank surname is matched as a case-insensitive fragment; absent filters list all patients.
     *
     * @param email the account email filter, null when absent
     * @param lastName the optional case-insensitive surname fragment
     * @param pageQuery the bound paging input, checked against this resource's allowed sort properties
     * @return the page of resource representations and pagination metadata
     */
    @Operation(
        summary = "Search patients",
        parameters = {
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page, default 0"),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "1-100, default 20"),
            @Parameter(
                name = "sort",
                in = ParameterIn.QUERY,
                description = "property[,asc|desc]; allowed: id, lastName, firstName, birthday"
            ),
        }
    )
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Page of patients"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid paging or sort",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping
    public PageResponse<PatientDto> search(
        @RequestParam(required = false) String email,
        @RequestParam(required = false) String lastName,
        @Parameter(hidden = true) @PageableDefault(size = 20, sort = "id") PageQuery pageQuery
    ) {
        return patientService.search(email, lastName, pageQuery.toPageable(SORTABLE));
    }

    /**
     * Replaces editable patient fields through PUT and returns the DTO with 200.
     *
     * <p>The owner stays unchanged; an omitted or null optional phone clears it.
     *
     * @param id the resource identifier
     * @param command the input values for this operation
     * @return the updated resource representation
     */
    @Operation(summary = "Replace editable patient data (owner cannot change)")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Updated"),
            @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "PATIENT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "ID_CARD_ALREADY_EXISTS",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PutMapping("/{id}")
    public PatientDto update(@PathVariable Long id, @Valid @RequestBody PatientUpdateCommand command) {
        return patientService.update(id, command);
    }

    /**
     * Deletes a patient without visit references and returns 204.
     *
     * @param id the resource identifier
     */
    @Operation(summary = "Delete a patient without visits")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(
                responseCode = "404",
                description = "PATIENT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "RESOURCE_IN_USE",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        patientService.delete(id);
    }
}
