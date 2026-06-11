package com.yammer.security;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Per-key login throttle to blunt credential-stuffing / brute force on {@code /auth/login}.
 * Keys are the client IP and the attempted username (tracked independently). After
 * {@link #MAX_FAILURES} failures within {@link #WINDOW} the key is blocked until the window
 * elapses; a successful login clears the key. In-memory and best-effort — single-instance
 * mitigation, not a distributed rate limiter.
 */
@Component
public class LoginAttemptService {

    private static final int MAX_FAILURES = 10;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempt(int count, Instant first) {
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** True if this key has exceeded the failure budget within the current window. */
    public boolean isBlocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (expired(a)) {
            attempts.remove(key, a);
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    /** Record a failed attempt, starting a fresh window if the previous one elapsed. */
    public void recordFailure(String key) {
        attempts.merge(key, new Attempt(1, Instant.now()),
                (prev, add) -> expired(prev) ? add : new Attempt(prev.count() + 1, prev.first()));
    }

    /** Clear the key after a successful login. */
    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private boolean expired(Attempt a) {
        return Instant.now().isAfter(a.first().plus(WINDOW));
    }
}
