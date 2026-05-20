package com.agentops.firewall.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link JwtService}. No Spring context — exercises the
 * issue/parse cycle, role-claim extraction, and tamper detection.
 */
class JwtServiceTest {

    private static final String SECRET =
            "agentops_unit_test_only_jwt_secret_at_least_thirtytwo_bytes_long_xx";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret(SECRET);
        props.setExpirationMinutes(60);
        props.setIssuer("agentops-firewall-test");
        jwtService = new JwtService(props);
    }

    @Test
    @DisplayName("issues a token that round-trips back to the same subject and role")
    void issueAndParseRoundtrip() {
        String token = jwtService.issueToken("admin", "ADMIN");

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("rejects a token signed with a different secret")
    void rejectsTamperedSignature() {
        String token = jwtService.issueToken("admin", "ADMIN");

        JwtProperties otherProps = new JwtProperties();
        otherProps.setSecret("a_completely_different_jwt_secret_at_least_thirtytwo_bytes_long");
        otherProps.setExpirationMinutes(60);
        otherProps.setIssuer("agentops-firewall-test");
        JwtService otherService = new JwtService(otherProps);

        assertThatThrownBy(() -> otherService.extractUsername(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("rejects an expired token")
    void rejectsExpiredToken() throws InterruptedException {
        JwtProperties shortLived = new JwtProperties();
        shortLived.setSecret(SECRET);
        shortLived.setExpirationMinutes(0); // expires immediately
        shortLived.setIssuer("agentops-firewall-test");
        JwtService shortService = new JwtService(shortLived);

        String token = shortService.issueToken("admin", "ADMIN");
        Thread.sleep(50);

        assertThatThrownBy(() -> shortService.extractUsername(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("getTokenLifetime reflects configured expiration minutes")
    void exposesTokenLifetime() {
        assertThat(jwtService.getTokenLifetime().toMinutes()).isEqualTo(60);
    }
}
