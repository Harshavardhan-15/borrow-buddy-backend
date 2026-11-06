package com.borrowbuddy.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory JWT denylist used for secure logout. Tokens are kept only until
 * their natural expiry. For a multi-instance production deployment, back this
 * with Redis (see docs/DEPLOYMENT.md).
 */
@Service
public class JwtBlacklistService {

    private final Map<String, Instant> revoked = new ConcurrentHashMap<>();

    public void revoke(String token, Instant expiresAt) {
        cleanup();
        if (expiresAt != null && expiresAt.isAfter(Instant.now())) {
            revoked.put(token, expiresAt);
        }
    }

    public boolean isRevoked(String token) {
        Instant expiry = revoked.get(token);
        if (expiry == null) {
            return false;
        }
        if (expiry.isBefore(Instant.now())) {
            revoked.remove(token);
            return false;
        }
        return true;
    }

    private void cleanup() {
        Instant now = Instant.now();
        revoked.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}
