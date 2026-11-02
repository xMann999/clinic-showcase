package com.example.clinic.controller;

import com.example.clinic.command.BookingCommand;
import com.example.clinic.dto.BookingSummaryDto;
import com.example.clinic.error.ApiError;
import com.example.clinic.facade.BookingFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Exposes HTTP operations for bookings.
 *
 * <h2>Responsibility</h2>
 * <p>Owns request binding and response status/header choices; delegates business work to the injected
 * facade. Lombok supplies constructor injection for final collaborators.
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
 * <p>The user and visit path identifiers select a booking subresource. The user identifier checks
 * ownership consistency and does not authenticate the caller.
 * The exception advice translates validation and business failures into HTTP errors; this controller
 * does not catch them. Writes are transactional in the delegated application layer.
 */
@Tag(name = "Bookings", description = "Booking a visit slot for a patient of a user account")
@RestController
@RequestMapping("/api/users/{userId}/visits/{visitId}/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingFacade bookingFacade;

    /**
     * Books the body-selected patient into the path-selected visit and returns 201.
     *
     * <p>Location is the current booking URI. The facade checks patient ownership and slot state.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     * @param command the input values for this operation
     * @param response the servlet response receiving the Location header
     * @return the established booking summary
     */
    @Operation(summary = "Book a free future slot for a patient of this account")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "201", description = "Booked; Location points to this booking"),
            @ApiResponse(
                responseCode = "400",
                description = "VALIDATION_FAILED / VISIT_IN_PAST",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND / PATIENT_NOT_FOUND / VISIT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "VISIT_ALREADY_BOOKED / OWNERSHIP_MISMATCH / CONCURRENT_MODIFICATION",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingSummaryDto book(
        @PathVariable Long userId,
        @PathVariable Long visitId,
        @Valid @RequestBody BookingCommand command,
        HttpServletResponse response
    ) {
        BookingSummaryDto summary = bookingFacade.book(userId, visitId, command.patientId());
        response.setHeader(
            HttpHeaders.LOCATION,
            ServletUriComponentsBuilder.fromCurrentRequestUri().build().toUriString()
        );
        return summary;
    }

    /**
     * Returns the account's booking summary with 200; an unbooked or differently owned slot is not found.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     * @return the requested resource representation
     */
    @Operation(summary = "Read the booking")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "200", description = "Booking summary"),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND / VISIT_NOT_FOUND / BOOKING_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @GetMapping
    public BookingSummaryDto get(@PathVariable Long userId, @PathVariable Long visitId) {
        return bookingFacade.get(userId, visitId);
    }

    /**
     * Cancels a booking and returns 204; an already free slot is a successful no-op.
     *
     * <p>A booking owned by another account is rejected rather than cancelled.
     *
     * @param userId the account identifier
     * @param visitId the visit identifier
     */
    @Operation(summary = "Cancel the booking (idempotent)")
    @ApiResponses(
        {
            @ApiResponse(responseCode = "204", description = "No booking after the call"),
            @ApiResponse(
                responseCode = "404",
                description = "USER_NOT_FOUND / VISIT_NOT_FOUND",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                responseCode = "409",
                description = "OWNERSHIP_MISMATCH",
                content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
        }
    )
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long userId, @PathVariable Long visitId) {
        bookingFacade.cancel(userId, visitId);
    }
}
