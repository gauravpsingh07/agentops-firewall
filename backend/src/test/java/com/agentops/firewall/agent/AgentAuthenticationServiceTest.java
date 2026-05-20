package com.agentops.firewall.agent;

import com.agentops.firewall.common.domain.enums.AgentStatus;
import com.agentops.firewall.common.error.AgentAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link AgentAuthenticationService}. Verifies the
 * uniform-failure-mode contract: every failure path raises the same
 * exception type so the controller cannot leak which precondition
 * failed. AgentRepository is mocked (interface). AgentKeyService is a
 * concrete class that Mockito cannot intercept on Java 25, so a real
 * instance backed by BCryptPasswordEncoder is used directly.
 */
class AgentAuthenticationServiceTest {

    private AgentRepository agentRepository;
    private AgentKeyService agentKeyService;
    private AgentAuthenticationService service;

    @BeforeEach
    void setUp() {
        agentRepository = Mockito.mock(AgentRepository.class);
        agentKeyService = new AgentKeyService(new BCryptPasswordEncoder());
        service = new AgentAuthenticationService(agentRepository, agentKeyService);
    }

    @Test
    @DisplayName("happy path returns the active agent")
    void happyPathReturnsAgent() {
        String raw = "agk_unit_test_key_value";
        String hash = agentKeyService.hash(raw);

        Agent agent = new Agent();
        agent.setId(UUID.randomUUID());
        agent.setName("agent-x");
        agent.setApiKeyHash(hash);
        agent.setStatus(AgentStatus.ACTIVE);
        Mockito.when(agentRepository.findByName("agent-x")).thenReturn(Optional.of(agent));

        Agent result = service.authenticate("agent-x", raw);
        assertThat(result).isSameAs(agent);
    }

    @Test
    @DisplayName("missing key throws AgentAuthenticationException")
    void missingKey() {
        assertThatThrownBy(() -> service.authenticate("agent-x", null))
                .isInstanceOf(AgentAuthenticationException.class);
        assertThatThrownBy(() -> service.authenticate("agent-x", ""))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    @DisplayName("missing agent identifier throws AgentAuthenticationException")
    void missingAgentId() {
        assertThatThrownBy(() -> service.authenticate(null, "raw"))
                .isInstanceOf(AgentAuthenticationException.class);
        assertThatThrownBy(() -> service.authenticate("", "raw"))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    @DisplayName("unknown agent throws AgentAuthenticationException")
    void unknownAgent() {
        Mockito.when(agentRepository.findByName("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.authenticate("ghost", "raw"))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    @DisplayName("non-ACTIVE agent (DISABLED) is rejected before key verification")
    void disabledAgent() {
        Agent agent = new Agent();
        agent.setName("disabled-agent");
        agent.setApiKeyHash("some-hash");
        agent.setStatus(AgentStatus.DISABLED);
        Mockito.when(agentRepository.findByName("disabled-agent")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.authenticate("disabled-agent", "raw"))
                .isInstanceOf(AgentAuthenticationException.class);
    }

    @Test
    @DisplayName("wrong key throws AgentAuthenticationException")
    void wrongKey() {
        String storedRaw = "agk_actual_key";
        String hash = agentKeyService.hash(storedRaw);

        Agent agent = new Agent();
        agent.setName("agent-x");
        agent.setApiKeyHash(hash);
        agent.setStatus(AgentStatus.ACTIVE);
        Mockito.when(agentRepository.findByName("agent-x")).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.authenticate("agent-x", "agk_wrong_key"))
                .isInstanceOf(AgentAuthenticationException.class);
    }
}