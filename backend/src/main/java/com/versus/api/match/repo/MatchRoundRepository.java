package com.versus.api.match.repo;

import com.versus.api.match.domain.MatchRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MatchRoundRepository extends JpaRepository<MatchRound, UUID> {
    long countByMatchId(UUID matchId);
}
