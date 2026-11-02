package com.example.clinic.controller;

import com.example.clinic.command.VisitCreateCommand;
import com.example.clinic.controller.paging.PageQuery;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.VisitDto;
import com.example.clinic.error.ApiError;
import com.example.clinic.repository.VisitSearchCriteria;
import com.example.clinic.service.VisitService;
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
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Exposes HTTP operations for visit slots.
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
 * <p>Local start times contain no offset and use the configured clinic zone, default Europe/Warsaw.
 * The exception advice translates validation and business failures into HTTP errors; this controller
 * does not catch them. Writes are transactional in the delegated application layer.
 */
@Tag(name = "Visits", description = "Visit slots. dateTime is local clinic time (Europe/Warsaw) without offset.")
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {

    /** HTTP sort properties accepted for this resource; services receive the validated pageable. */
    private static final Set<String> SORTABLE = Set.of("dateTime", "durationInMinutes", "id");

    private final VisitService visitService;

    /**
     * Creates a free slot and returns 201 with the new resource URI in Location.
     *
     * @param command the input values for this operation
     * @param response the servlet response receiving the Location header
     * @return the created resource representation
     */
    @Operation(
        summary = "Create a free slot for a doctor",
        description = "Start at a full quarter-hour, 15-240 minutes, not in the past, ending the same day."
    )
    @ApiResponses(
        {
            @ApiResponse(responseCode = "201", description = "Created"),
            @ApiResponse(
                responseCode = "400",
                description = "VALIDATION_FAILED / VISIT_IN_PAST / VISIT_INVALID_TIME",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "DOCTOR_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "VISIT_OVERLAP",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitDto create(@Valid @RequestBody VisitCreateCommand command, HttpServletResponse response) {
        VisitDto created = visitService.createSlot(command);
        String location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUriString();
        response.setHeader(HttpHeaders.LOCATION, location);
        return created;
    }

    /**
     * Returns a visit DTO with 200, or propagates a missing-resource error.
     *
     * @param id the resource identifier
     * @return the requested resource representation
     */
    @Operation(summary = "Get a visit")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(
                responseCode = "404",
                description = "VISIT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping("/{id}")
    public VisitDto get(@PathVariable Long id) {
        return visitService.get(id);
    }

    /**
     * Returns a 200 page using optional doctor, patient, time and availability query filters.
     *
     * <p>ISO local date-time bounds select {@code from <= dateTime < to}; both bounds, when present,
     * must increase. Default sorting is start then identifier. Null availability adds no availability condition.
     *
     * @param doctorId the doctor identifier
     * @param patientId the patient identifier; null in a free-slot response
     * @param from the inclusive local start bound, or null when absent
     * @param to the exclusive local start bound, or null when absent
     * @param available true for free slots, false for booked slots, null for no restriction
     * @param pageQuery the bound paging input, checked against this resource's allowed sort properties
     * @return the page of resource representations and pagination metadata
     */
    @Operation(
        summary = "Search visits",
        parameters = {
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page, default 0"),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "1-100, default 20"),
            @Parameter(
                name = "sort",
                in = ParameterIn.QUERY,
                description = "property[,asc|desc]; allowed: dateTime, durationInMinutes, id"
            ),
        }
    )
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Page of visits"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid filter, paging or sort",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping
    public PageResponse<VisitDto> search(
        @RequestParam(required = false) Long doctorId,
        @RequestParam(required = false) Long patientId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
        @RequestParam(required = false) Boolean available,
        @Parameter(hidden = true) @PageableDefault(size = 20, sort = "dateTime") PageQuery pageQuery
    ) {
        VisitSearchCriteria criteria = new VisitSearchCriteria(doctorId, patientId, from, to, available);
        return visitService.search(criteria, pageQuery.toPageable(SORTABLE));
    }

    /**
     * Deletes a free slot and returns 204; booked slots are rejected.
     *
     * @param id the resource identifier
     */
    @Operation(summary = "Delete a free slot")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(
                responseCode = "404",
                description = "VISIT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "RESOURCE_IN_USE (visit is booked)",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        visitService.delete(id);
    }
}
