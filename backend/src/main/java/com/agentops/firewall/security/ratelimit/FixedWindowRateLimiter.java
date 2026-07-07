package com.agentops.firewall.security.ratelimit;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A tiny, dependency-free fixed-window rate limiter. Each key is allowed
 * {@code capacity} requests per {@code windowSeconds}; the window boundary is
 * aligned to epoch seconds so no background eviction thread is needed — a
 * key's counter simply resets when its window index rolls over.
 *
 * <p>Fixed-window is intentionally simple (it permits a burst at a window
 * boundary) and is more than enough to blunt brute-force and flood attempts
 * for this service. The map is bounded in practice by the number of distinct
 * client IPs / agent keys seen within a window.
 */
@Component
public class FixedWindowRateLimiter {

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @return {@code true} if the request is within the allowance,
     *         {@code false} if it should be rejected.
     */
    public boolean tryAcquire(String key, int capacity, int windowSeconds) {
        if (capacity <= 0 || windowSeconds <= 0) {
            return true; // misconfigured rule: fail open rather than lock everyone out
        }
        long currentWindow = Instant.now().getEpochSecond() / windowSeconds;
        Window updated = windows.compute(key, (k, existing) -> {
            if (existing == null || existing.window != currentWindow) {
                return new Window(currentWindow);
            }
            existing.count++;
            return existing;
        });
        return updated.count <= capacity;
    }

    private static final class Window {
        private final long window;
        private int count;

        private Window(long window) {
            this.window = window;
            this.count = 1;
        }
    }
}
