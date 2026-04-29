package com.versus.api.match.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_rounds", indexes = {
        @Index(name = "idx_match_rounds_match", columnList = "match_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchRound {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "match_id", nullable = false)
    private UUID matchId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "round_number", nullable = false)
    private Integer roundNumber;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
