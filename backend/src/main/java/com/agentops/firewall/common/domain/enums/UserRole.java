package com.agentops.firewall.common.domain.enums;

/**
 * Application-level user roles. Mapped to Spring Security authorities as
 * {@code ROLE_<NAME>} (handled by {@code SecurityConfig}).
 */
public enum UserRole {
    ADMIN,
    REVIEWER,
    VIEWER
}
