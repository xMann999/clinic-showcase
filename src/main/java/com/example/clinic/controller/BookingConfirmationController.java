package com.example.clinic.controller;

import com.example.clinic.dto.BookingConfirmationDto;
import com.example.clinic.facade.BookingFacade;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes the local booking confirmation history for an existing account.
 *
 * <h2>Responsibility</h2>
 * <p>Binds the account identifier and delegates the existing-account/history workflow to the booking facade.
 *
 * <h2>How it works</h2>
 * <p>GET returns a DTO array with 200, including an empty array when no receipts remain. Missing accounts
 * use the existing USER_NOT_FOUND error and 404 through the global exception advice.
 *
 * <h2>Design choices</h2>
 * <p>The side-effect-free read exposes historical receipts separately from current booking status.
 * It returns at most 20 entries, newest insertion first, as an immutable snapshot.
 *
 * <h2>Important details</h2>
 * <p>This showcase has no authentication: userId identifies a recipient, not permission to read their data.
 * Receipts are local to one process, disappear on restart and can be evicted or absent after channel failure.
 */
@RestController
@RequestMapping("/api/users/{userId}/booking-confirmations")
@RequiredArgsConstructor
public class BookingConfirmationController {

    private final BookingFacade bookingFacade;

    /**
     * Reads retained confirmations without marking or changing them.
     * @param userId existing account identifier
     * @return historical receipts, possibly empty
     * @throws com.example.clinic.error.DomainException if the account does not exist
     */
    @GetMapping
    public List<BookingConfirmationDto> get(@PathVariable Long userId) {
        return bookingFacade.getConfirmations(userId);
    }
}
