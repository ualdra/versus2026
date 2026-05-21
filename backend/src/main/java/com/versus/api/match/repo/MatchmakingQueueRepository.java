package com.versus.api.match.repo;

import com.versus.api.match.GameMode;
import com.versus.api.match.domain.MatchmakingQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingQueueRepository extends JpaRepository<MatchmakingQueue, UUID> {
    Optional<MatchmakingQueue> findByUserId(UUID userId);
    void deleteByUserId(UUID userId);
    List<MatchmakingQueue> findByModeOrderByEnteredAtAsc(GameMode mode);
}
