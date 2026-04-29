package com.versus.api.questions.domain;

import com.versus.api.questions.QuestionStatus;
import com.versus.api.questions.QuestionType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "questions", indexes = {
        @Index(name = "idx_questions_status_type_category", columnList = "status,type,category")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionType type;

    @Column(length = 64)
    private String category;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "scraped_at")
    private Instant scrapedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private QuestionStatus status;

    // NUMERIC-specific
    @Column(name = "correct_value", precision = 20, scale = 4)
    private BigDecimal correctValue;

    @Column(length = 32)
    private String unit;

    @Column(name = "tolerance_percent", precision = 6, scale = 2)
    private BigDecimal tolerancePercent;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (status == null) status = QuestionStatus.PENDING_REVIEW;
        if (type == QuestionType.NUMERIC && tolerancePercent == null) {
            tolerancePercent = new BigDecimal("5");
        }
    }
}
