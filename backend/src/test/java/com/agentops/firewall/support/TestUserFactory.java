package com.agentops.firewall.support;

import com.agentops.firewall.common.domain.enums.UserRole;
import com.agentops.firewall.user.User;
import com.agentops.firewall.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Convenience factory for seeding application users in integration tests.
 * Encapsulates the BCrypt encoding so individual test classes do not need
 * to deal with it.
 */
@Component
public class TestUserFactory {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public TestUserFactory(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User createUser(String username, String password, UserRole role) {
        User user = new User(username, passwordEncoder.encode(password), role);
        return userRepository.save(user);
    }

    public void deleteAll() {
        userRepository.deleteAll();
    }
}
