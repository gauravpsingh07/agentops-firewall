package com.agentops.firewall.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Credentials accepted by POST /api/auth/login. */
public record LoginRequest(
        @NotBlank(message = "username is required")
        @Size(max = 80, message = "username must be at most 80 characters")
        String username,

        @NotBlank(message = "password is required")
        @Size(max = 200, message = "password must be at most 200 characters")
        String password
) {
}
