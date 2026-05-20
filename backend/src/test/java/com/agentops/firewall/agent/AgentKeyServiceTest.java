package com.agentops.firewall.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link AgentKeyService}. No Spring context. Verifies
 * key shape, BCrypt round-trip, and key-uniqueness across invocations.
 */
class AgentKeyServiceTest {

    private AgentKeyService service;

    @BeforeEach
    void setUp() {
        service = new AgentKeyService(new BCryptPasswordEncoder());
    }

    @Test
    @DisplayName("generated keys begin with agk_ and are unique")
    void generatedKeysAreUnique() {
        String k1 = service.generateRawKey();
        String k2 = service.generateRawKey();
        assertThat(k1).startsWith("agk_");
        assertThat(k2).startsWith("agk_");
        assertThat(k1).isNotEqualTo(k2);
        assertThat(k1.length()).isGreaterThan(20);
    }

    @Test
    @DisplayName("hash and matches form a valid round-trip")
    void hashAndMatchesRoundtrip() {
        String raw = service.generateRawKey();
        String hash = service.hash(raw);
        assertThat(hash).startsWith("$2");
        assertThat(service.matches(raw, hash)).isTrue();
        assertThat(service.matches(raw + "tamper", hash)).isFalse();
    }

    @Test
    @DisplayName("matches returns false for null inputs")
    void matchesNullInputs() {
        assertThat(service.matches(null, "anything")).isFalse();
        assertThat(service.matches("anything", null)).isFalse();
        assertThat(service.matches(null, null)).isFalse();
    }

    @Test
    @DisplayName("matches returns false for a malformed stored hash")
    void matchesMalformedHash() {
        assertThat(service.matches("agk_xyz", "not-a-real-bcrypt-hash")).isFalse();
    }
}