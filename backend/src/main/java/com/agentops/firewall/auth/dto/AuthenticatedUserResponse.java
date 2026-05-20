package com.agentops.firewall.auth.dto;

import java.util.UUID;

/** Public projection of an authenticated user; safe to return from /me and /login. */
public record AuthenticatedUserResponse(
        UUID id,
        String username,
        String role
) {
}
