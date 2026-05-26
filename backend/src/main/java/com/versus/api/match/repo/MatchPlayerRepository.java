package com.versus.api.match.repo;

import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, MatchPlayerId> {

    Optional<MatchPlayer> findByIdMatchIdAndIdUserId(UUID matchId, UUID userId);

    List<MatchPlayer> findByIdMatchId(UUID matchId);
}
