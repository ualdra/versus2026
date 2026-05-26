package com.versus.api.stats.repo;

import com.versus.api.match.GameMode;
import com.versus.api.stats.domain.Ranking;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RankingRepository extends JpaRepository<Ranking, UUID> {
    Optional<Ranking> findByUserIdAndMode(UUID userId, GameMode mode);

    List<Ranking> findByUserId(UUID userId);

    Page<Ranking> findByMode(GameMode mode, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Ranking r WHERE r.userId = :userId AND r.mode = :mode")
    Optional<Ranking> findByUserIdAndModeForUpdate(@Param("userId") UUID userId,
                                                   @Param("mode") GameMode mode);

    @Query("""
            SELECT COUNT(r) FROM Ranking r
            WHERE r.mode = :mode
              AND (
                    r.rating > :rating
                    OR (r.rating = :rating AND r.wins > :wins)
                    OR (r.rating = :rating AND r.wins = :wins AND r.updatedAt < :updatedAt)
              )
            """)
    long countBetterRank(@Param("mode") GameMode mode,
                         @Param("rating") int rating,
                         @Param("wins") int wins,
                         @Param("updatedAt") Instant updatedAt);
}
