package com.borrowbuddy.dto.student;

import com.borrowbuddy.dto.auth.UserDto;
import com.borrowbuddy.dto.item.ItemDto;

import java.util.List;

public record StudentProfileDto(
        UserDto user,
        long listedItems,
        long totalBorrowed,
        long totalLent,
        long completedBorrows,
        long completedLends,
        double averageRating,
        long reviewCount,
        List<ItemDto> items
) {
}
