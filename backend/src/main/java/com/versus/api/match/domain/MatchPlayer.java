package com.versus.api.match.domain;

import com.versus.api.match.MatchResult;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_players")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchPlayer {

    @EmbeddedId
    private MatchPlayerId id;

    @Column(name = "lives_remaining", nullable = false)
    private Integer livesRemaining;

    @Column(nullable = false)
    private Integer score;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "best_streak_in_match", nullable = false)
    private Integer bestStreakInMatch;

    @Column(name = "rounds_played", nullable = false)
    private Integer roundsPlayed;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private MatchResult result;

    @PrePersist
    void prePersist() {
        if (score == null) score = 0;
        if (currentStreak == null) currentStreak = 0;
        if (bestStreakInMatch == null) bestStreakInMatch = 0;
        if (roundsPlayed == null) roundsPlayed = 0;
    }
}
