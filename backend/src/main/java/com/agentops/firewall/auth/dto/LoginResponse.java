package com.agentops.firewall.auth.dto;

/** Body returned by POST /api/auth/login on success. */
public record LoginResponse(
        String token,
        String tokenType,
        long expiresInSeconds,
        AuthenticatedUserResponse user
) {
}
