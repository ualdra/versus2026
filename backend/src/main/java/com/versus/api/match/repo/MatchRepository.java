package com.versus.api.match.repo;

import com.versus.api.match.domain.Match;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    Optional<Match> findByRoomCode(String roomCode);

    // Admin dashboard
    long countByCreatedAtAfter(Instant from);

    // Admin dashboard — finished matches grouped by game mode
    @Query("""
            SELECT m.mode AS mode, COUNT(m) AS count
            FROM Match m
            WHERE m.status = com.versus.api.match.MatchStatus.FINISHED
            GROUP BY m.mode
            """)
    List<Object[]> countFinishedByMode();

    // User match history and stats
    @Query(value = """
            SELECT m.* FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            WHERE mp.user_id = :userId AND m.status = 'FINISHED'
            ORDER BY m.finished_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            WHERE mp.user_id = :userId AND m.status = 'FINISHED'
            """,
            nativeQuery = true)
    Page<Match> findFinishedByUserId(@Param("userId") UUID userId, Pageable pageable);

    @Query(value = """
            SELECT m.* FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            WHERE mp.user_id = :userId AND m.status = 'FINISHED' AND m.mode = :mode
            ORDER BY m.finished_at DESC
            """,
            countQuery = """
            SELECT COUNT(*) FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            WHERE mp.user_id = :userId AND m.status = 'FINISHED' AND m.mode = :mode
            """,
            nativeQuery = true)
    Page<Match> findFinishedByUserIdAndMode(@Param("userId") UUID userId,
                                            @Param("mode") String mode,
                                            Pageable pageable);

    @Query(value = """
            SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (m.finished_at - m.created_at))), 0)
            FROM matches m
            JOIN match_players mp ON mp.match_id = m.id
            WHERE mp.user_id = :userId AND m.status = 'FINISHED'
            """, nativeQuery = true)
    Long sumPlayTimeSecondsByUserId(@Param("userId") UUID userId);
}
