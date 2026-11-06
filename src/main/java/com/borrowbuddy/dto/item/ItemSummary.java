package com.borrowbuddy.dto.item;

import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;

public record ItemSummary(
        Long id,
        String itemName,
        String category,
        String imageUrl,
        ItemCondition itemCondition,
        AvailabilityStatus availabilityStatus,
        Long ownerId,
        String ownerName
) {
}
