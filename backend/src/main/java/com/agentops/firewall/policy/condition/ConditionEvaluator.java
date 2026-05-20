package com.agentops.firewall.policy.condition;

import com.agentops.firewall.policy.PolicyCondition;
import com.agentops.firewall.policy.PolicyEvaluationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Evaluates a single {@link PolicyCondition} against a
 * {@link PolicyEvaluationContext}.
 *
 * <p>The field path supports three shapes:
 * <ul>
 *   <li>{@code actionType}, {@code resource}, {@code riskLevel} — top-level scalars on the action.</li>
 *   <li>{@code agent.status} / {@code agent.name} — selected agent attributes.</li>
 *   <li>{@code metadata.<key>} — a free-form key in the inbound metadata JSON.</li>
 * </ul>
 *
 * <p>Values are stored as strings on {@link PolicyCondition}. For
 * collection-valued operators ({@code IN}, {@code NOT_IN}) the value is
 * parsed as a JSON array; if parsing fails, the operator falls back to a
 * comma-separated split.
 */
@Component
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
    private static final TypeReference<List<String>> LIST_OF_STRING = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ConditionEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean matches(PolicyCondition condition, PolicyEvaluationContext ctx) {
        ConditionOperator operator = ConditionOperator.fromString(condition.getOperator()).orElse(null);
        if (operator == null) {
            log.warn("Unknown operator '{}' on condition {}; treating as non-match.",
                    condition.getOperator(), condition.getId());
            return false;
        }
        Object actual = resolveField(condition.getField(), ctx);
        String configured = condition.getValue();
        return switch (operator) {
            case EQUALS       -> stringEquals(actual, configured);
            case NOT_EQUALS   -> !stringEquals(actual, configured);
            case IN           -> inList(actual, configured);
            case NOT_IN       -> !inList(actual, configured);
            case EXISTS       -> actual != null && !blank(actual);
            case NOT_EXISTS   -> actual == null || blank(actual);
            case CONTAINS     -> stringContains(actual, configured);
            case NOT_CONTAINS -> !stringContains(actual, configured);
            case MATCHES      -> regexMatches(actual, configured);
            case GTE          -> compare(actual, configured) >= 0;
            case LTE          -> compare(actual, configured) <= 0;
        };
    }

    private Object resolveField(String field, PolicyEvaluationContext ctx) {
        if (field == null || field.isBlank()) return null;
        String[] parts = field.split("\\.", 2);
        String head = parts[0];
        return switch (head) {
            case "actionType" -> ctx.actionType() == null ? null : ctx.actionType().name();
            case "resource"   -> ctx.resource();
            case "riskLevel"  -> ctx.riskLevel() == null ? null : ctx.riskLevel().name();
            case "agent"      -> resolveAgentField(parts.length > 1 ? parts[1] : "", ctx);
            case "metadata"   -> resolveMetadataField(parts.length > 1 ? parts[1] : "", ctx);
            default -> {
                log.debug("Unknown policy condition field root '{}'.", head);
                yield null;
            }
        };
    }

    private Object resolveAgentField(String tail, PolicyEvaluationContext ctx) {
        if (ctx.agent() == null || tail.isBlank()) return null;
        return switch (tail) {
            case "name"   -> ctx.agent().getName();
            case "status" -> ctx.agent().getStatus() == null ? null : ctx.agent().getStatus().name();
            default -> null;
        };
    }

    private Object resolveMetadataField(String tail, PolicyEvaluationContext ctx) {
        if (tail.isBlank()) return null;
        Map<String, Object> meta = ctx.metadata();
        String[] segments = tail.split("\\.");
        Object current = meta;
        for (String seg : segments) {
            if (current instanceof Map<?, ?> map) {
                current = map.get(seg);
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
    }

    private boolean stringEquals(Object actual, String configured) {
        if (actual == null) return configured == null;
        if (actual instanceof Boolean b) {
            return b.toString().equalsIgnoreCase(configured);
        }
        if (actual instanceof Number n) {
            return n.toString().equals(configured);
        }
        return Objects.toString(actual).equalsIgnoreCase(configured);
    }

    private boolean stringContains(Object actual, String configured) {
        if (actual == null || configured == null) return false;
        return Objects.toString(actual).toLowerCase(Locale.ROOT)
                .contains(configured.toLowerCase(Locale.ROOT));
    }

    private boolean regexMatches(Object actual, String configured) {
        if (actual == null || configured == null) return false;
        try {
            return Pattern.compile(configured).matcher(Objects.toString(actual)).matches();
        } catch (PatternSyntaxException ex) {
            log.warn("Invalid regex in policy condition value: {}", configured);
            return false;
        }
    }

    private boolean inList(Object actual, String configured) {
        if (actual == null || configured == null) return false;
        List<String> values = parseListValue(configured);
        String actualStr = Objects.toString(actual);
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(actualStr));
    }

    private List<String> parseListValue(String configured) {
        String trimmed = configured.trim();
        if (trimmed.startsWith("[")) {
            try {
                return objectMapper.readValue(trimmed, LIST_OF_STRING);
            } catch (JsonProcessingException ex) {
                log.warn("Policy condition value looks like JSON but failed to parse: {}",
                        ex.getOriginalMessage());
            }
        }
        return List.of(trimmed.split("\\s*,\\s*"));
    }

    private int compare(Object actual, String configured) {
        if (actual == null || configured == null) return 0;
        try {
            double a = Double.parseDouble(Objects.toString(actual));
            double b = Double.parseDouble(configured);
            return Double.compare(a, b);
        } catch (NumberFormatException ex) {
            return Objects.toString(actual).compareToIgnoreCase(configured);
        }
    }

    private boolean blank(Object value) {
        if (value == null) return true;
        if (value instanceof CharSequence cs) return cs.toString().isBlank();
        return false;
    }
}