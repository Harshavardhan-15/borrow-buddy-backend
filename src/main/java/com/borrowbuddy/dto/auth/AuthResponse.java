package com.borrowbuddy.dto.auth;

import java.time.Instant;

public record AuthResponse(String token, String tokenType, long expiresInMs, UserDto user) {
}
