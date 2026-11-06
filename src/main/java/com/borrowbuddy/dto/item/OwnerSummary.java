package com.borrowbuddy.dto.item;

public record OwnerSummary(
        Long id,
        String name,
        String profileImage,
        double averageRating,
        long reviewCount
) {
}
