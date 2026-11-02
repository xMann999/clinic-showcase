package com.example.clinic.service;

import com.example.clinic.dto.BookingConfirmationDto;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Keeps a small local history of booking confirmations for each recipient.
 *
 * <h2>Responsibility</h2>
 * <p>Stores immutable receipts in this process only; it sends no email or SMS and writes no database rows.
 *
 * <h2>How it works</h2>
 * <p>One map computation serializes updates for each account. Readers receive immutable list snapshots.
 * The newest accepted receipt comes first, in insertion order rather than clock order, even on clock ties.
 *
 * <h2>Design choices</h2>
 * <p>A maximum of 20 receipts per recipient keeps histories small. Duplicate event IDs are ignored while
 * retained, without reordering them. Independent recipients need no application-wide lock.
 *
 * <h2>Important details</h2>
 * <p>Restart loses all receipts; instances do not share them. Eviction also forgets deduplication IDs,
 * so this is not exactly-once delivery. The number of recipient buckets is not globally bounded.
 * No retry or durable delivery is provided, and cancellation does not update historical receipts.
 */
@Component
public class BookingConfirmationInbox {

    public static final int MAX_PER_USER = 20;
    private final ConcurrentHashMap<Long, List<BookingConfirmationDto>> receipts = new ConcurrentHashMap<>();

    /**
     * Atomically retains a receipt unless its event ID already exists in this recipient's history.
     * @param userId recipient account identifier
     * @param confirmation immutable receipt to retain
     */
    public void add(Long userId, BookingConfirmationDto confirmation) {
        receipts.compute(userId, (id, existing) -> {
            var previous = existing == null ? List.<BookingConfirmationDto>of() : existing;
            if (previous.stream().anyMatch(item -> item.eventId().equals(confirmation.eventId()))) {
                return previous;
            }
            var updated = new ArrayList<BookingConfirmationDto>();
            updated.add(confirmation);
            updated.addAll(previous.subList(0, Math.min(previous.size(), MAX_PER_USER - 1)));
            return List.copyOf(updated);
        });
    }

    /**
     * Returns at most 20 receipts, newest insertion first, without changing state.
     * @param userId recipient identifier; the booking facade checks existence before exposing history
     * @return immutable snapshot, empty if nothing is retained for this identifier
     */
    public List<BookingConfirmationDto> find(Long userId) {
        return receipts.getOrDefault(userId, List.of());
    }
}
