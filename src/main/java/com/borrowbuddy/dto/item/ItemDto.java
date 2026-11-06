package com.borrowbuddy.dto.item;

import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;

import java.time.Instant;
import java.time.LocalDate;

public record ItemDto(
        Long id,
        String itemName,
        String category,
        String description,
        String imageUrl,
        ItemCondition itemCondition,
        AvailabilityStatus availabilityStatus,
        boolean availableNow,
        boolean reservedNow,
        LocalDate nextAvailableDate,
        OwnerSummary owner,
        Instant createdAt,
        boolean ownedByMe
) {
}
