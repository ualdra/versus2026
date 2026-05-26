package com.versus.api.config;

import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "versus.seed", name = "enabled", havingValue = "true")
public class DevSeedConfig {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedDevData() {
        return args -> seedUsers();
    }

    private void seedUsers() {
        seedUser("player", "player@versus.com", "player123", Role.PLAYER);
        seedUser("moderator", "moderator@versus.com", "moderator123", Role.MODERATOR);
        seedUser("admin", "admin@versus.com", "admin123", Role.ADMIN);
    }

    private void seedUser(String username, String email, String password, Role role) {
        if (users.existsByEmail(email) || users.existsByUsername(username)) {
            return;
        }
        users.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .isActive(true)
                .build());
    }
}
