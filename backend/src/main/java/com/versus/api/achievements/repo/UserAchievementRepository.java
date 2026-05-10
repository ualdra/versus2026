package com.versus.api.achievements.repo;

import com.versus.api.achievements.domain.UserAchievement;
import com.versus.api.achievements.domain.UserAchievementId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserAchievementRepository extends JpaRepository<UserAchievement, UserAchievementId> {
    List<UserAchievement> findByIdUserId(UUID userId);
    boolean existsByIdUserIdAndIdAchievementId(UUID userId, UUID achievementId);
}
