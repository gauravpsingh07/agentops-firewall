package com.agentops.firewall.security.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private final FixedWindowRateLimiter limiter = new FixedWindowRateLimiter();

    @Test
    @DisplayName("permits up to capacity then rejects within the same window")
    void permitsUpToCapacity() {
        int capacity = 3;
        for (int i = 0; i < capacity; i++) {
            assertThat(limiter.tryAcquire("k", capacity, 60))
                    .as("request %d should be allowed", i + 1)
                    .isTrue();
        }
        assertThat(limiter.tryAcquire("k", capacity, 60)).isFalse();
        assertThat(limiter.tryAcquire("k", capacity, 60)).isFalse();
    }

    @Test
    @DisplayName("separate keys have independent allowances")
    void keysAreIndependent() {
        assertThat(limiter.tryAcquire("a", 1, 60)).isTrue();
        assertThat(limiter.tryAcquire("a", 1, 60)).isFalse();
        // Different key is unaffected.
        assertThat(limiter.tryAcquire("b", 1, 60)).isTrue();
    }

    @Test
    @DisplayName("a misconfigured rule fails open")
    void misconfiguredRuleFailsOpen() {
        assertThat(limiter.tryAcquire("k", 0, 60)).isTrue();
        assertThat(limiter.tryAcquire("k", 5, 0)).isTrue();
    }
}
