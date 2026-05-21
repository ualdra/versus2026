package com.versus.api.auth;

import com.versus.api.common.exception.ApiException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding-window in-memory rate limiter for password reset requests.
 * Limits each IP to MAX_REQUESTS within WINDOW to prevent email flooding.
 */
@Component
public class PasswordResetRateLimiter {

    private static final int MAX_REQUESTS = 3;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final ConcurrentHashMap<String, ArrayDeque<Instant>> log = new ConcurrentHashMap<>();

    public void check(String ip) {
        log.compute(ip, (key, deque) -> {
            if (deque == null) deque = new ArrayDeque<>();
            Instant cutoff = Instant.now().minus(WINDOW);
            while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
                deque.pollFirst();
            }
            if (deque.size() >= MAX_REQUESTS) {
                throw ApiException.tooManyRequests(
                        "Demasiadas solicitudes de recuperación de contraseña. Inténtalo de nuevo en 10 minutos.");
            }
            deque.addLast(Instant.now());
            return deque;
        });
    }
}
