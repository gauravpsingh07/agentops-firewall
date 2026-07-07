package com.agentops.firewall.security.ratelimit;

import com.agentops.firewall.common.error.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Rejects requests that exceed the configured per-client allowance with a
 * uniform {@code 429 Too Many Requests}. Runs ahead of the security filter
 * chain so floods are shed before any authentication or database work.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} — keyed by client IP (brute-force guard).</li>
 *   <li>{@code POST /api/agent-actions} and {@code .../{id}/complete} — keyed by
 *       a hash of the agent key (IP fallback), so one noisy agent can't
 *       starve the ingestion path.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String INGEST_PATH = "/api/agent-actions";

    private final FixedWindowRateLimiter limiter;
    private final RateLimitProperties properties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(FixedWindowRateLimiter limiter,
                           RateLimitProperties properties,
                           ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Target target = properties.isEnabled() ? resolveTarget(request) : null;
        if (target != null
                && !limiter.tryAcquire(target.key, target.rule.getCapacity(), target.rule.getWindowSeconds())) {
            writeTooManyRequests(request, response, target.rule.getWindowSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Target resolveTarget(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return null;
        }
        String uri = request.getRequestURI();
        if (LOGIN_PATH.equals(uri)) {
            return new Target("login:" + clientIp(request), properties.getLogin());
        }
        if (INGEST_PATH.equals(uri) || (uri.startsWith(INGEST_PATH + "/") && uri.endsWith("/complete"))) {
            return new Target("ingest:" + ingestionKey(request), properties.getIngestion());
        }
        return null;
    }

    /** Prefer the agent key (hashed) so limiting is per-agent; fall back to IP. */
    private String ingestionKey(HttpServletRequest request) {
        String agentKey = request.getHeader("X-Agent-Key");
        if (StringUtils.hasText(agentKey)) {
            return "k:" + sha256Hex(agentKey);
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8); // first 8 bytes is plenty for a bucket key
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is always available; degrade to a non-reversible hashCode.
            return Integer.toHexString(value.hashCode());
        }
    }

    private void writeTooManyRequests(HttpServletRequest request,
                                      HttpServletResponse response,
                                      int windowSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(windowSeconds));
        ApiError body = ApiError.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Rate limit exceeded. Please retry after " + windowSeconds + " seconds.",
                request.getRequestURI());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private record Target(String key, RateLimitProperties.Rule rule) {
    }
}
