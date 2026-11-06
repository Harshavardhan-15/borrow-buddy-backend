package com.borrowbuddy.dto.borrow;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateBorrowRequest(
        @NotNull(message = "Item is required")
        Long itemId,

        @NotNull(message = "Start date is required")
        @FutureOrPresent(message = "Start date cannot be in the past")
        LocalDate startDate,

        @NotNull(message = "Return date is required")
        @Future(message = "Return date must be in the future")
        LocalDate returnDate,

        @Size(max = 1000, message = "Message is too long")
        String message
) {
}
