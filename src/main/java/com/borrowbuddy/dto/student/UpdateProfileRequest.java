package com.borrowbuddy.dto.student;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name is too long")
        String name,

        @NotBlank(message = "Student ID is required")
        @Size(max = 50, message = "Student ID is too long")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Student ID may only contain letters, numbers, dot, dash and underscore")
        String studentId,

        @Size(max = 500, message = "Image URL is too long")
        String profileImage
) {
}
