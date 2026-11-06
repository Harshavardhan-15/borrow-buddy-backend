package com.borrowbuddy.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "College email is required")
        @Email(message = "Enter a valid email address")
        String collegeEmail,

        @NotBlank(message = "Password is required")
        String password
) {
}
