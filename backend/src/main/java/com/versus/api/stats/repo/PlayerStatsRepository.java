package com.versus.api.stats.repo;

import com.versus.api.match.GameMode;
import com.versus.api.stats.domain.PlayerStats;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlayerStatsRepository extends JpaRepository<PlayerStats, UUID> {
    Optional<PlayerStats> findByUserIdAndMode(UUID userId, GameMode mode);
    List<PlayerStats> findByUserId(UUID userId);
}
