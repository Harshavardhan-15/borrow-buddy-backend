package com.borrowbuddy.dto.review;

import com.borrowbuddy.dto.item.OwnerSummary;

import java.time.Instant;

public record ReviewDto(
        Long id,
        OwnerSummary reviewer,
        OwnerSummary reviewedStudent,
        Long transactionId,
        String itemName,
        int rating,
        String comment,
        Instant createdAt
) {
}
