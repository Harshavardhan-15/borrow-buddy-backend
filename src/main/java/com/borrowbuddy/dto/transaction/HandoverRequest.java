package com.borrowbuddy.dto.transaction;

import jakarta.validation.constraints.NotBlank;

public record HandoverRequest(
        @NotBlank(message = "QR token is required")
        String token
) {
}
