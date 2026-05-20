package com.agentops.firewall.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the {@code agentops.security.jwt.*} settings in
 * application.yml. All values are env-driven; the YAML defaults are
 * placeholders and must be overridden in non-local environments.
 */
@ConfigurationProperties(prefix = "agentops.security.jwt")
public class JwtProperties {

    /** HMAC secret. Must be at least 32 bytes for HS256. */
    private String secret;

    /** Access-token lifetime in minutes. */
    private long expirationMinutes = 60;

    /** {@code iss} claim. */
    private String issuer = "agentops-firewall";

    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }

    public long getExpirationMinutes() { return expirationMinutes; }
    public void setExpirationMinutes(long expirationMinutes) { this.expirationMinutes = expirationMinutes; }

    public String getIssuer() { return issuer; }
    public void setIssuer(String issuer) { this.issuer = issuer; }
}
