package com.agentops.firewall.policy.condition;

import java.util.Optional;

/**
 * Supported policy-condition operators. Each policy condition combines a
 * field path (e.g. {@code metadata.recipientDomain}), an operator from
 * this enum, and a string-encoded value. The set is intentionally small
 * and explicit so policy authors are not surprised by hidden behavior.
 */
public enum ConditionOperator {

    EQUALS,
    NOT_EQUALS,
    IN,
    NOT_IN,
    EXISTS,
    NOT_EXISTS,
    CONTAINS,
    NOT_CONTAINS,
    MATCHES,
    GTE,
    LTE;

    public static Optional<ConditionOperator> fromString(String raw) {
        if (raw == null) return Optional.empty();
        try {
            return Optional.of(ConditionOperator.valueOf(raw.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
