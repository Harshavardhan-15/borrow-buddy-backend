package com.borrowbuddy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 120, message = "Name is too long")
        String name,

        @NotBlank(message = "College email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email is too long")
        String collegeEmail,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                message = "Password must contain at least one letter and one number")
        String password,

        @NotBlank(message = "Please confirm your password")
        String confirmPassword,

        @NotBlank(message = "Student ID is required")
        @Size(max = 50, message = "Student ID is too long")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "Student ID may only contain letters, numbers, dot, dash and underscore")
        String studentId
) {
}
