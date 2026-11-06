package com.borrowbuddy.dto.review;

import java.util.List;

public record ReviewSummaryDto(double averageRating, long reviewCount, List<ReviewDto> reviews) {
}
