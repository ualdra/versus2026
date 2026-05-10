package com.versus.api.achievements;

import com.versus.api.achievements.domain.Achievement;
import com.versus.api.achievements.repo.AchievementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

@Configuration
@RequiredArgsConstructor
public class AchievementSeedConfig {

    private final AchievementRepository achievements;

    @Bean
    CommandLineRunner seedAchievements() {
        return args -> seedCatalog();
    }

    @Transactional
    void seedCatalog() {
        for (AchievementCatalog.Seed seed : AchievementCatalog.seeds()) {
            if (achievements.existsByKey(seed.key())) {
                continue;
            }
            achievements.save(Achievement.builder()
                    .key(seed.key())
                    .name(seed.name())
                    .description(seed.description())
                    .iconKey(seed.iconKey())
                    .category(seed.category())
                    .build());
        }
    }
}
