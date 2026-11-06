package com.borrowbuddy.dto.auth;

import com.borrowbuddy.entity.enums.Role;

import java.time.Instant;

public record UserDto(
        Long id,
        String name,
        String collegeEmail,
        String studentId,
        Role role,
        String profileImage,
        double averageRating,
        long reviewCount,
        Instant createdAt
) {
}
