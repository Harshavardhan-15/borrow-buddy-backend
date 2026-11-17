package com.borrowbuddy.security;

import com.borrowbuddy.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory rate limit for the unauthenticated auth endpoints.
 *
 * <p>Ten attempts per minute per client IP (configurable) makes credential
 * stuffing and registration spam impractical without adding infrastructure.
 * Behind Render's proxy the client address comes from {@code X-Forwarded-For}
 * because {@code server.forward-headers-strategy=framework} is enabled.</p>
 *
 * <p>State is per instance, so a multi-instance deployment should move this to
 * the proxy or Redis.</p>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final long WINDOW_MILLIS = 60_000L;
    private static final int PURGE_THRESHOLD = 1_000;

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRequests;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${app.rate-limit.enabled:true}") boolean enabled,
                           @Value("${app.rate-limit.auth-per-minute:10}") int maxRequests) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRequests = maxRequests;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled || !isProtected(request)) {
            chain.doFilter(request, response);
            return;
        }

        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key(request), ignored -> new Window(now));
        long retryAfterSeconds;
        synchronized (window) {
            if (now - window.start >= WINDOW_MILLIS) {
                window.start = now;
                window.count = 0;
            }
            window.count++;
            if (window.count <= maxRequests) {
                retryAfterSeconds = 0;
            } else {
                retryAfterSeconds = Math.max(1, (window.start + WINDOW_MILLIS - now + 999) / 1000);
            }
        }

        if (retryAfterSeconds > 0) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(
                    "Too many attempts. Try again in " + retryAfterSeconds + " seconds."));
            return;
        }

        purgeExpired(now);
        chain.doFilter(request, response);
    }

    private boolean isProtected(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && PROTECTED_PATHS.contains(request.getRequestURI());
    }

    private String key(HttpServletRequest request) {
        return request.getRemoteAddr() + "|" + request.getRequestURI();
    }

    private void purgeExpired(long now) {
        if (windows.size() < PURGE_THRESHOLD) {
            return;
        }
        windows.entrySet().removeIf(entry -> now - entry.getValue().start >= WINDOW_MILLIS);
    }

    private static final class Window {
        private long start;
        private int count;

        private Window(long start) {
            this.start = start;
        }
    }
}
