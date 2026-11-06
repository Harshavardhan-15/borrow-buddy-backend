package com.borrowbuddy.dto.transaction;

import java.time.Instant;

public record QrCodeDto(String token, Instant expiresAt, String imageDataUrl) {
}
