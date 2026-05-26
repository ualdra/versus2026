package com.versus.api.achievements.repo;

import com.versus.api.achievements.domain.Achievement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AchievementRepository extends JpaRepository<Achievement, UUID> {
    boolean existsByKey(String key);
    Optional<Achievement> findByKey(String key);
    List<Achievement> findByKeyIn(Collection<String> keys);
}
