package com.versus.api.match.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "match_answers", indexes = {
        @Index(name = "idx_match_answers_round", columnList = "round_id"),
        @Index(name = "idx_match_answers_user", columnList = "user_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchAnswer {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "answer_given", length = 255)
    private String answerGiven;

    @Column(name = "deviation")
    private Double deviation;

    @Column(name = "life_delta", nullable = false)
    private Integer lifeDelta;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "answered_at", nullable = false, updatable = false)
    private Instant answeredAt;

    @PrePersist
    void onCreate() {
        if (answeredAt == null) answeredAt = Instant.now();
    }
}
