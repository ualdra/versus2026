package com.versus.api.match.domain;

import com.versus.api.match.GameMode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "matchmaking_queue", indexes = {
        @Index(name = "idx_mmqueue_mode_entered", columnList = "mode,entered_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchmakingQueue {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private GameMode mode;

    @Column(name = "entered_at", nullable = false, updatable = false)
    private Instant enteredAt;

    @PrePersist
    void onCreate() {
        if (enteredAt == null) enteredAt = Instant.now();
    }
}
