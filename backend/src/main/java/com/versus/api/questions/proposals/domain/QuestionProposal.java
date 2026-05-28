package com.versus.api.questions.proposals.domain;

import com.versus.api.questions.QuestionType;
import com.versus.api.questions.proposals.ProposalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "question_proposals", indexes = {
        @Index(name = "idx_question_proposals_author_created", columnList = "author_id,created_at"),
        @Index(name = "idx_question_proposals_status_created", columnList = "status,created_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionProposal {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QuestionType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String text;

    @Column(name = "proposed_answer", nullable = false, length = 512)
    private String proposedAnswer;

    @Column(name = "alternative_answer", length = 512)
    private String alternativeAnswer;

    @Column(nullable = false, length = 64)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProposalStatus status;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "reject_reason", length = 512)
    private String rejectReason;

    @Column(name = "source_url", length = 1024)
    private String sourceUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (status == null) status = ProposalStatus.PENDING;
    }
}
