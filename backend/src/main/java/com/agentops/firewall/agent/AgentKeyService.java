package com.agentops.firewall.agent;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates and verifies agent API keys.
 *
 * <p>Keys have the shape {@code agk_<base64url-random>} so they are easy
 * to spot in logs as agent keys (the firewall must never log the full
 * key — only the {@code agk_} prefix can be considered safe). 24 random
 * bytes give 192 bits of entropy, which is comfortably above any
 * practical guessing threat for a local demo environment.
 *
 * <p>Storage uses BCrypt via the application's standard
 * {@link PasswordEncoder}. The raw key is shown to the operator exactly
 * once at create/rotate time and is never persisted.
 */
@Service
public class AgentKeyService {

    private static final String KEY_PREFIX = "agk_";
    private static final int RANDOM_BYTES = 24;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final PasswordEncoder passwordEncoder;

    public AgentKeyService(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public String generateRawKey() {
        byte[] bytes = new byte[RANDOM_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return KEY_PREFIX + ENCODER.encodeToString(bytes);
    }

    public String hash(String rawKey) {
        return passwordEncoder.encode(rawKey);
    }

    public boolean matches(String rawKey, String storedHash) {
        if (rawKey == null || storedHash == null) {
            return false;
        }
        try {
            return passwordEncoder.matches(rawKey, storedHash);
        } catch (IllegalArgumentException ex) {
            // Defensive: a corrupted hash should be treated as mismatch.
            return false;
        }
    }
}
