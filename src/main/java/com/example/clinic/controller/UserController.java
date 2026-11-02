package com.example.clinic.controller;

import com.example.clinic.command.PasswordChangeCommand;
import com.example.clinic.command.UserCreateCommand;
import com.example.clinic.command.UserUpdateCommand;
import com.example.clinic.controller.paging.PageQuery;
import com.example.clinic.dto.PageResponse;
import com.example.clinic.dto.UserDto;
import com.example.clinic.error.ApiError;
import com.example.clinic.service.UserService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Exposes HTTP operations for accounts.
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
 * <p>Account DTOs omit password hashes; password mutation has its own narrow PATCH.
 * The exception advice translates validation and business failures into HTTP errors; this controller
 * does not catch them. Writes are transactional in the delegated application layer.
 */
@Tag(name = "Users", description = "User accounts (e-mail, password hash, role)")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    /** HTTP sort properties accepted for this resource; services receive the validated pageable. */
    private static final Set<String> SORTABLE = Set.of("id", "email", "role");

    private final UserService userService;

    /**
     * Creates an account and returns 201 with the new resource URI in Location.
     *
     * @param command the input values for this operation
     * @param response the servlet response receiving the Location header
     * @return the created resource representation
     */
    @Operation(summary = "Create a user account")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "201", description = "Created; Location points to the new user"),
            @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "EMAIL_ALREADY_EXISTS",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserCreateCommand command, HttpServletResponse response) {
        UserDto created = userService.create(command);
        String location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.id())
            .toUriString();
        response.setHeader(HttpHeaders.LOCATION, location);
        return created;
    }

    /**
     * Returns an account DTO without its password hash with 200.
     *
     * @param id the resource identifier
     * @return the requested resource representation
     */
    @Operation(summary = "Get a user account (never returns the password)")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Found"),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping("/{id}")
    public UserDto get(@PathVariable Long id) {
        return userService.get(id);
    }

    /**
     * Returns a 200 page of account DTOs with allowed sort fields.
     *
     * @param pageQuery the bound paging input, checked against this resource's allowed sort properties
     * @return the page of resource representations and pagination metadata
     */
    @Operation(
        summary = "List user accounts",
        parameters = {
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "Zero-based page, default 0"),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "1-100, default 20"),
            @Parameter(
                name = "sort",
                in = ParameterIn.QUERY,
                description = "property[,asc|desc]; allowed: id, email, role"
            ),
        }
    )
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Page of users"),
            @ApiResponse(
                responseCode = "400",
                description = "Invalid paging or sort",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping
    public PageResponse<UserDto> list(
        @Parameter(hidden = true) @PageableDefault(size = 20, sort = "id") PageQuery pageQuery
    ) {
        return userService.list(pageQuery.toPageable(SORTABLE));
    }

    /**
     * Replaces email and role through PUT and returns the updated DTO with 200.
     *
     * @param id the resource identifier
     * @param command the input values for this operation
     * @return the updated resource representation
     */
    @Operation(summary = "Replace editable account data (email, role)")
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
                description = "USER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "EMAIL_ALREADY_EXISTS",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserUpdateCommand command) {
        return userService.update(id, command);
    }

    /**
     * Hashes and replaces the required password through PATCH and returns 204 without a body.
     *
     * @param id the resource identifier
     * @param command the input values for this operation
     */
    @Operation(summary = "Change password")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "204", description = "Password changed"),
            @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PatchMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@PathVariable Long id, @Valid @RequestBody PasswordChangeCommand command) {
        userService.changePassword(id, command);
    }

    /**
     * Deletes an account only when it has no patients or doctor profile and returns 204.
     *
     * @param id the resource identifier
     */
    @Operation(summary = "Delete a user account without patients or doctor profile")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "204", description = "Deleted"),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND",
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
        userService.delete(id);
    }
}
