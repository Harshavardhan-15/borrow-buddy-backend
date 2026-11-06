package com.borrowbuddy.dto.transaction;

import com.borrowbuddy.dto.item.ItemSummary;
import com.borrowbuddy.dto.item.OwnerSummary;
import com.borrowbuddy.entity.enums.TransactionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TransactionDto(
        Long id,
        Long requestId,
        ItemSummary item,
        OwnerSummary borrower,
        OwnerSummary owner,
        LocalDate startDate,
        LocalDate returnDate,
        Instant handoverDate,
        Instant actualReturnDate,
        TransactionStatus status,
        boolean overdue,
        boolean qrAvailable,
        boolean canConfirmHandover,
        boolean canConfirmReturn,
        boolean canReview,
        Long reviewId,
        Instant createdAt
) {
}
