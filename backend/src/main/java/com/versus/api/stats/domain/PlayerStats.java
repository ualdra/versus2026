package com.versus.api.stats.domain;

import com.versus.api.match.GameMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Entity
@Table(name = "player_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uk_player_stats_user_mode", columnNames = {"user_id", "mode"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerStats {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameMode mode;

    @Column(name = "games_played", nullable = false)
    private Integer gamesPlayed;

    @Column(name = "games_won", nullable = false)
    private Integer gamesWon;

    @Column(name = "avg_deviation")
    private Double avgDeviation;

    @Column(name = "best_streak", nullable = false)
    private Integer bestStreak;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @PrePersist
    void prePersist() {
        if (gamesPlayed == null) gamesPlayed = 0;
        if (gamesWon == null) gamesWon = 0;
        if (bestStreak == null) bestStreak = 0;
        if (currentStreak == null) currentStreak = 0;
    }
}
