package com.agentops.firewall.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Issues and verifies HS256-signed JSON Web Tokens. The signing key is
 * derived from the configured secret; the secret must be at least 32 bytes
 * (enforced by jjwt at key-creation time).
 */
@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final Duration tokenLifetime;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.tokenLifetime = Duration.ofMinutes(properties.getExpirationMinutes());
    }

    /**
     * Issue a token with {@code sub=username} and a custom {@code role} claim.
     */
    public String issueToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiry = now.plus(tokenLifetime);
        return Jwts.builder()
                .issuer(properties.getIssuer())
                .subject(username)
                .claims(Map.of("role", role))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                // Pin HS256 explicitly. Without this, jjwt 0.12.x infers
                // HS512 for >=64-byte keys, which then refuses to verify
                // tokens against shorter (but still >=32-byte) keys. HS256
                // gives us stable behaviour across all valid secret sizes
                // and is a sensible default for a portfolio-grade demo.
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parse and verify a token. Throws JwtException variants if the token
     * is malformed, expired, or signed with a different key.
     */
    public Jws<Claims> parse(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .requireIssuer(properties.getIssuer())
                .build()
                .parseSignedClaims(token);
    }

    public String extractUsername(String token) {
        return parse(token).getPayload().getSubject();
    }

    public String extractRole(String token) {
        Object role = parse(token).getPayload().get("role");
        return role != null ? role.toString() : null;
    }

    public Duration getTokenLifetime() {
        return tokenLifetime;
    }
}
