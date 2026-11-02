package com.example.clinic.service;

import com.example.clinic.dto.BookingConfirmationDto;
import com.example.clinic.facade.VisitBookedEvent;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Prepares a readable receipt after the booking transaction commits.
 *
 * <h2>Responsibility</h2>
 * <p>Owns the secondary confirmation effect using an injected inbox and application clock.
 * Booking validation and persistence remain with the facade and its services.
 *
 * <h2>How it works</h2>
 * <p>Spring registers a transaction-bound callback on publication and invokes it after successful commit.
 * Unlike an ordinary event listener, it does not prepare receipts before commit. With fallback disabled,
 * publication outside a transaction is ignored. This application's synchronous configuration runs the
 * callback on the committing thread; after commit does not mean asynchronous execution.
 *
 * <h2>Design choices</h2>
 * <p>The event supplies the whole snapshot, so no lazy entity access or database write is needed after
 * completion. A local runtime channel failure is diagnosed without changing the committed booking result.
 *
 * <h2>Important details</h2>
 * <p>The process can stop between commit and this callback. Neither Spring events nor the inbox guarantee
 * durable delivery, cross-instance communication or retry. A missing receipt does not mean booking failed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingConfirmationListener {

    private final BookingConfirmationInbox inbox;
    private final Clock clock;

    /**
     * Retains a historical receipt after commit; runtime channel errors leave booking success intact.
     * @param event immutable booking snapshot published inside the transaction
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVisitBooked(VisitBookedEvent event) {
        try {
            inbox.add(
                event.userId(),
                new BookingConfirmationDto(
                    event.eventId(),
                    event.visitId(),
                    event.dateTime(),
                    event.zoneId(),
                    event.durationInMinutes(),
                    event.doctor(),
                    clock.instant()
                )
            );
        } catch (RuntimeException failure) {
            log.warn(
                "Booking confirmation unavailable: event={}, failureType={}",
                event.eventId(),
                failure.getClass().getSimpleName()
            );
        }
    }
}
