package com.versus.api.match.repo;

import com.versus.api.match.domain.MatchmakingQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchmakingQueueRepository extends JpaRepository<MatchmakingQueue, UUID> {
}
