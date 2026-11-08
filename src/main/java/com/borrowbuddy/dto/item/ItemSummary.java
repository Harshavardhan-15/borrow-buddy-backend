package com.borrowbuddy.dto.item;

import com.borrowbuddy.entity.enums.AvailabilityStatus;
import com.borrowbuddy.entity.enums.ItemCondition;

import java.math.BigDecimal;

public record ItemSummary(
        Long id,
        String itemName,
        String category,
        String imageUrl,
        ItemCondition itemCondition,
        AvailabilityStatus availabilityStatus,
        BigDecimal pricePerDay,
        BigDecimal securityDeposit,
        Long ownerId,
        String ownerName
) {
}
