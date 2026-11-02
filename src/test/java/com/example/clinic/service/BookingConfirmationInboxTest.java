package com.example.clinic.service;

import static org.assertj.core.api.Assertions.*;

import com.example.clinic.dto.BookingConfirmationDto;
import com.example.clinic.dto.DoctorSummaryDto;
import com.example.clinic.support.FixedClockConfiguration;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class BookingConfirmationInboxTest {

    @Test
    void immutableSnapshotsInsertionOrderLimitAndBoundedDeduplication() {
        var inbox = new BookingConfirmationInbox();
        var first = receipt(1);
        inbox.add(1L, first);
        var snapshot = inbox.find(1L);
        for (int i = 2; i <= 21; i++) inbox.add(1L, receipt(i));
        assertThat(snapshot).containsExactly(first);
        assertThatThrownBy(() -> snapshot.clear()).isInstanceOf(UnsupportedOperationException.class);
        var retained = inbox.find(1L);
        assertThat(retained).hasSize(20);
        assertThat(retained)
            .extracting(BookingConfirmationDto::eventId)
            .containsExactly(
                java.util.stream.IntStream.iterate(21, i -> i - 1)
                    .limit(20)
                    .mapToObj(i -> new UUID(0, i))
                    .toArray(UUID[]::new)
            );
        inbox.add(1L, receipt(10));
        assertThat(inbox.find(1L)).isEqualTo(retained);
        inbox.add(1L, first);
        assertThat(inbox.find(1L).getFirst()).isEqualTo(first);
        assertThat(inbox.find(2L)).isEmpty();
    }

    @Test
    void concurrentWritersDoNotLoseDistinctReceiptsOrExposeMutableLists() throws Exception {
        var inbox = new BookingConfirmationInbox();
        try (var executor = Executors.newFixedThreadPool(4)) {
            var jobs = java.util.stream.IntStream.rangeClosed(1, 20)
                .<java.util.concurrent.Callable<Void>>mapToObj(
                    i ->
                        () -> {
                            inbox.add(1L, receipt(i));
                            inbox.add(1L, receipt(i));
                            assertThat(inbox.find(1L).size()).isBetween(1, 20);
                            return null;
                        }
                )
                .toList();
            for (var result : executor.invokeAll(jobs)) result.get();
        }
        assertThat(inbox.find(1L)).hasSize(20);
        assertThat(inbox.find(1L)).extracting(BookingConfirmationDto::eventId).doesNotHaveDuplicates();
    }

    private BookingConfirmationDto receipt(int id) {
        return new BookingConfirmationDto(
            new UUID(0, id),
            10L,
            FixedClockConfiguration.TOMORROW_9AM,
            "Europe/Warsaw",
            30,
            new DoctorSummaryDto(2L, "Jan Test", "Cardiology"),
            FixedClockConfiguration.fixedClock().instant()
        );
    }
}
