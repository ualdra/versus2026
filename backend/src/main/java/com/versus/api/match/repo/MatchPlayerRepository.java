package com.versus.api.match.repo;

import com.versus.api.match.domain.MatchPlayer;
import com.versus.api.match.domain.MatchPlayerId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchPlayerRepository extends JpaRepository<MatchPlayer, MatchPlayerId> {
}
