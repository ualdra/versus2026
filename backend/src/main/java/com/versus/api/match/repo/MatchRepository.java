package com.versus.api.match.repo;

import com.versus.api.match.domain.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {
    Optional<Match> findByRoomCode(String roomCode);
}
