package com.borrowbuddy.dto.borrow;

import com.borrowbuddy.dto.item.ItemSummary;
import com.borrowbuddy.dto.item.OwnerSummary;
import com.borrowbuddy.entity.enums.RequestStatus;
import com.borrowbuddy.entity.enums.TransactionStatus;

import java.time.Instant;
import java.time.LocalDate;

public record BorrowRequestDto(
        Long id,
        ItemSummary item,
        OwnerSummary borrower,
        OwnerSummary owner,
        LocalDate requestDate,
        LocalDate startDate,
        LocalDate returnDate,
        String message,
        RequestStatus status,
        Instant createdAt,
        Long transactionId,
        TransactionStatus transactionStatus
) {
}
