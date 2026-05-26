package com.versus.api.config;

import com.versus.api.cards.CardImportService;
import com.versus.api.cards.dto.CardImportRequest;
import com.versus.api.cards.repo.CardRepository;
import com.versus.api.users.Role;
import com.versus.api.users.domain.User;
import com.versus.api.users.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Configuration
@Profile("dev")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "versus.seed", name = "enabled", havingValue = "true")
public class DevSeedConfig {

    private final UserRepository users;
    private final CardRepository cards;
    private final CardImportService cardImportService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    CommandLineRunner seedDevData() {
        return args -> {
            seedUsers();
            seedCards();
        };
    }

    private void seedCards() {
        if (cards.count() > 0) {
            return;
        }
        log.info("Seeding cards from scraper JSON...");
        var result = cardImportService.importFrom(new CardImportRequest(null, false));
        log.info("Cards seed: totalRead={}, inserted={}, skippedDuplicates={}, errors={}",
                result.totalRead(), result.inserted(), result.skippedDuplicates(), result.errors().size());
        if (!result.errors().isEmpty()) {
            result.errors().forEach(e -> log.warn("Card import error: {}", e));
        }
    }

    private void seedUsers() {
        seedUser("player", "player@versus.com", "player123", Role.PLAYER);
        seedUser("moderator", "moderator@versus.com", "moderator123", Role.MODERATOR);
        seedUser("admin", "admin@versus.com", "admin123", Role.ADMIN);
    }

    private User seedUser(String username, String email, String password, Role role) {
        return users.findByEmail(email)
                .or(() -> users.findByUsername(username))
                .orElseGet(() -> users.save(User.builder()
                        .username(username)
                        .email(email)
                        .passwordHash(passwordEncoder.encode(password))
                        .role(role)
                        .isActive(true)
                        .enabled(true)
                        .build()));
    }
}
