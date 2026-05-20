package com.agentops.firewall.common.error;

/** One per invalid field in a bean-validation failure. */
public record FieldValidationError(String field, String message) {
}
