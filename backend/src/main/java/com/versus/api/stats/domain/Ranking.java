package com.versus.api.stats.domain;

import com.versus.api.match.GameMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "rankings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_rankings_user_mode", columnNames = {"user_id", "mode"})
        },
        indexes = {
                @Index(name = "idx_rankings_mode_rating", columnList = "mode,rating DESC,wins DESC"),
                @Index(name = "idx_rankings_user_mode", columnList = "user_id,mode")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Ranking {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameMode mode;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false)
    private Integer wins;

    @Column(nullable = false)
    private Integer losses;

    @Column(name = "win_streak", nullable = false)
    private Integer winStreak;

    @Column(name = "last_delta", nullable = false)
    private Integer lastDelta;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (rating == null) rating = 1000;
        if (wins == null) wins = 0;
        if (losses == null) losses = 0;
        if (winStreak == null) winStreak = 0;
        if (lastDelta == null) lastDelta = 0;
        touch();
    }

    @PreUpdate
    void preUpdate() {
        touch();
    }

    void touch() {
        updatedAt = Instant.now();
    }
}
