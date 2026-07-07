package com.agentops.firewall.security.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the {@link RateLimitFilter}. Bound from
 * {@code agentops.security.rate-limit.*}.
 */
@ConfigurationProperties(prefix = "agentops.security.rate-limit")
public class RateLimitProperties {

    /** Master switch; disabled in the test profile for deterministic suites. */
    private boolean enabled = true;

    /** Limit for {@code POST /api/auth/login}, keyed by client IP. */
    private Rule login = new Rule(10, 60);

    /** Limit for agent action submit/complete, keyed by agent key (IP fallback). */
    private Rule ingestion = new Rule(60, 60);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Rule getLogin() {
        return login;
    }

    public void setLogin(Rule login) {
        this.login = login;
    }

    public Rule getIngestion() {
        return ingestion;
    }

    public void setIngestion(Rule ingestion) {
        this.ingestion = ingestion;
    }

    /** A fixed-window allowance: at most {@code capacity} requests per {@code windowSeconds}. */
    public static class Rule {
        private int capacity;
        private int windowSeconds;

        public Rule() {
        }

        public Rule(int capacity, int windowSeconds) {
            this.capacity = capacity;
            this.windowSeconds = windowSeconds;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
