package com.agentops.firewall.common.domain.enums;

/**
 * The discrete set of risky operations an AI agent can request through the
 * firewall. The taxonomy is intentionally small and explicit so that
 * policies can be authored against well-known buckets.
 */
public enum ActionType {
    SEND_EMAIL,
    DELETE_FILE,
    READ_SECRET,
    WRITE_DATABASE,
    CALL_EXTERNAL_API,
    DEPLOY_CODE,
    ACCESS_CUSTOMER_DATA,
    CREATE_GITHUB_PR,
    RUN_TERMINAL_COMMAND
}
